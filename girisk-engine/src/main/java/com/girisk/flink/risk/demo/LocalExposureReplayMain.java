package com.girisk.flink.risk.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.MatchExposureAggregator.ExposureSummary;
import com.girisk.flink.risk.grid.MatchExposureAggregator.ScenarioExposure;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.limit.MatchTriggerAcceptance;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import com.fasterxml.jackson.databind.node.ArrayNode;
import redis.clients.jedis.Jedis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline replay (same gates as Flink) → Redis materialised view for Console exposure board.
 *
 * <pre>
 * java -cp girisk-engine/target/girisk-engine-1.0.0.jar \
 *   com.girisk.flink.risk.demo.LocalExposureReplayMain \
 *   --file girisk-engine/src/test/resources/germany-vs-paraguay-orders.csv \
 *   --redis-host 127.0.0.1
 * </pre>
 */
public final class LocalExposureReplayMain {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parseArgs(args);
        Path file = Path.of(opts.getOrDefault("file", ""));
        if (opts.getOrDefault("file", "").isBlank() || !file.toFile().isFile()) {
            System.err.println("用法: --file <orders.csv> [--redis-host 127.0.0.1] [--redis-port 6379]");
            System.err.println("      [--fixture-id germany-paraguay] [--home Germany] [--away Paraguay]");
            System.err.println("      [--delta 0.2] [--seed 5000] [--max-worst-loss 200000]");
            System.err.println("      [--seed-out target/demo-exposure/sports-seed.json]");
            System.err.println("      [--skip-redis true]  # 仅写 seed-out / fixture-view.json");
            System.exit(2);
        }

        String fixtureId = opts.getOrDefault("fixture-id", "germany-paraguay");
        String home = opts.getOrDefault("home", "Germany");
        String away = opts.getOrDefault("away", "Paraguay");
        double delta = Double.parseDouble(opts.getOrDefault("delta", "0.2"));
        double seed = Double.parseDouble(opts.getOrDefault("seed", "5000"));
        double maxWorst = Double.parseDouble(opts.getOrDefault("max-worst-loss", "200000"));
        String redisHost = opts.getOrDefault("redis-host", "127.0.0.1");
        int redisPort = Integer.parseInt(opts.getOrDefault("redis-port", "6379"));
        String seedOut = opts.getOrDefault("seed-out", "");

        List<FootballSportsOrder> orders = OrderCsvLoader.load(file, fixtureId, home, away);
        ScoreGridParams grid = ScoreGridParams.fromMap(Map.of("score", "0:0", "grid", "6"));

        ExposureSummary noRiskExposure = MatchExposureAggregator.summarize(orders, grid.grid);
        ScenarioExposure noRiskWorst = worst(noRiskExposure);

        List<FootballSportsOrder> accepted = new ArrayList<>();
        int limitReject = 0;
        int exposureReject = 0;
        double acceptedStakeYuan = 0;
        for (FootballSportsOrder order : orders) {
            EnrichedFootballOrder trigger =
                    new EnrichedFootballOrder(order, System.currentTimeMillis(), fixtureId);
            MatchTriggerAcceptance decision =
                    MatchTriggerAcceptance.evaluate(
                            accepted, trigger, false, grid.grid, delta, seed, maxWorst, true);
            double stakeExact = order.stakeCents() / 100.0;
            if (decision.rejectReason == MatchTriggerAcceptance.RejectReason.NONE) {
                accepted.add(order);
                acceptedStakeYuan += stakeExact;
            } else if (decision.rejectReason == MatchTriggerAcceptance.RejectReason.LIMIT) {
                limitReject++;
            } else {
                exposureReject++;
            }
        }
        ExposureSummary withRiskExposure = MatchExposureAggregator.summarize(accepted, grid.grid);
        ScenarioExposure withRiskWorst = worst(withRiskExposure);

        ObjectNode stats = MAPPER.createObjectNode();
        stats.put("acceptedCount", accepted.size());
        stats.put("rejectedLimit", limitReject);
        stats.put("rejectedExposure", exposureReject);
        stats.put("rejectedTotal", limitReject + exposureReject);
        stats.put("totalOrders", orders.size());
        stats.put("acceptedStakeYuan", round2(acceptedStakeYuan));
        stats.put("noRiskWorstPnlYuan", round2(noRiskWorst.bookmakerPnlCents / 100.0));
        stats.put(
                "noRiskWorstScore",
                noRiskWorst.scenario.homeGoals + ":" + noRiskWorst.scenario.awayGoals);
        stats.put("withRiskWorstPnlYuan", round2(withRiskWorst.bookmakerPnlCents / 100.0));
        stats.put(
                "withRiskWorstScore",
                withRiskWorst.scenario.homeGoals + ":" + withRiskWorst.scenario.awayGoals);
        stats.put("delta", delta);
        stats.put("seedPayoutYuan", seed);
        stats.put("maxWorstLossYuan", maxWorst);

        long worstLossCents = Math.abs(withRiskWorst.bookmakerPnlCents);
        String worstScore =
                withRiskWorst.scenario.homeGoals + ":" + withRiskWorst.scenario.awayGoals;

        Map<String, Double> stakeBySel = new LinkedHashMap<>();
        Map<String, Double> payoutBySel = new LinkedHashMap<>();
        stakeBySel.put("home", 0.0);
        stakeBySel.put("draw", 0.0);
        stakeBySel.put("away", 0.0);
        payoutBySel.put("home", 0.0);
        payoutBySel.put("draw", 0.0);
        payoutBySel.put("away", 0.0);
        for (FootballSportsOrder o : accepted) {
            String sel = toConsoleSelection(o.selection);
            if (sel == null) {
                continue;
            }
            double stake = o.stakeCents() / 100.0;
            stakeBySel.merge(sel, stake, Double::sum);
            payoutBySel.merge(sel, stake * o.odds, Double::sum);
        }

        ObjectNode seedDoc = MAPPER.createObjectNode();
        seedDoc.put("matchCode", fixtureId);
        seedDoc.put("homeTeam", home);
        seedDoc.put("awayTeam", away);
        seedDoc.put("sportCode", "football");
        seedDoc.put("leagueCode", "FRIENDLY");
        seedDoc.put("leagueName", "国际友谊");
        seedDoc.put("delta", delta);
        seedDoc.put("exposureThreshold", maxWorst);
        ArrayNode groups = seedDoc.putArray("groups");
        ObjectNode g1 = groups.addObject();
        g1.put("marketType", "ONE_X_TWO");
        g1.put("line", "");
        ObjectNode stakesNode = g1.putObject("stakes");
        ObjectNode payoutsNode = g1.putObject("payouts");
        for (String sel : List.of("home", "draw", "away")) {
            stakesNode.put(sel, round2(stakeBySel.get(sel)));
            payoutsNode.put(sel, round2(payoutBySel.get(sel)));
        }

        Map<String, String> fixtureHash = new LinkedHashMap<>();
        fixtureHash.put("fixtureId", fixtureId);
        fixtureHash.put("homeTeam", home);
        fixtureHash.put("awayTeam", away);
        fixtureHash.put("worstLossCents", String.valueOf(worstLossCents));
        fixtureHash.put("worstScore", worstScore);
        fixtureHash.put("confirmedOrders", String.valueOf(accepted.size()));
        fixtureHash.put("pendingReserved", "0");
        fixtureHash.put("liveScore", "0:0");
        fixtureHash.put("updatedAt", String.valueOf(System.currentTimeMillis()));
        fixtureHash.put("replayStats", MAPPER.writeValueAsString(stats));
        fixtureHash.put("rawSnapshot", MAPPER.writeValueAsString(stats));

        if (!seedOut.isBlank()) {
            Path out = Path.of(seedOut);
            Path dir = out.getParent() != null ? out.getParent() : Path.of(".");
            Files.createDirectories(dir);
            Files.writeString(out, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(seedDoc));
            // Companion file for Console classpath bootstrap (no Redis required to generate).
            ObjectNode viewDoc = MAPPER.createObjectNode();
            viewDoc.put("fixtureId", fixtureId);
            viewDoc.put("worstLossCents", worstLossCents);
            ObjectNode fields = viewDoc.putObject("hash");
            for (Map.Entry<String, String> e : fixtureHash.entrySet()) {
                fields.put(e.getKey(), e.getValue());
            }
            Path viewOut = dir.resolve("fixture-view.json");
            Files.writeString(viewOut, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(viewDoc));
            System.out.println("fixture-view: " + viewOut);
        }

        String skipRedis = opts.getOrDefault("skip-redis", "false");
        if (!"true".equalsIgnoreCase(skipRedis)) {
            try (Jedis jedis = new Jedis(redisHost, redisPort, 3000)) {
                jedis.ping();
                String key = "girisk:view:fixture:" + fixtureId;
                jedis.hset(key, fixtureHash);
                jedis.expire(key, 7 * 24 * 3600);
                jedis.zadd("girisk:view:top:worstloss", worstLossCents, fixtureId);

                // Console RedisExposureStore keys — ready after sports_match row is seeded via API
                String matchKeys = "sports:matchkeys:" + fixtureId;
                jedis.del(matchKeys);
                for (String sel : List.of("home", "draw", "away")) {
                    String sk = "sports:stake:" + fixtureId + ":ONE_X_TWO::" + sel;
                    String pk = "sports:payout:" + fixtureId + ":ONE_X_TWO::" + sel;
                    jedis.set(sk, String.valueOf(round2(stakeBySel.get(sel))));
                    jedis.set(pk, String.valueOf(round2(payoutBySel.get(sel))));
                    jedis.expire(sk, 7 * 24 * 3600);
                    jedis.expire(pk, 7 * 24 * 3600);
                    jedis.sadd(matchKeys, sk, pk);
                }
            }
        }

        System.out.println();
        System.out.println("========== LocalExposureReplay → Redis ==========");
        System.out.printf(Locale.ROOT, "fixtureId=%s  %s vs %s%n", fixtureId, home, away);
        System.out.printf(
                Locale.ROOT,
                "接单%d 拦截%d(LIMIT%d/EXPOSURE%d) 无风控%.2f@%s 有风控%.2f@%s 接单本金%.2f%n",
                accepted.size(),
                limitReject + exposureReject,
                limitReject,
                exposureReject,
                noRiskWorst.bookmakerPnlCents / 100.0,
                noRiskWorst.scenario.homeGoals + ":" + noRiskWorst.scenario.awayGoals,
                withRiskWorst.bookmakerPnlCents / 100.0,
                worstScore,
                acceptedStakeYuan);
        System.out.printf(
                Locale.ROOT,
                "1X2 接单本金 home/draw/away = %.2f / %.2f / %.2f%n",
                stakeBySel.get("home"),
                stakeBySel.get("draw"),
                stakeBySel.get("away"));
        System.out.printf(
                Locale.ROOT,
                "Redis: girisk:view:fixture:%s  (host=%s:%d)%n",
                fixtureId,
                redisHost,
                redisPort);
        if (!seedOut.isBlank()) {
            System.out.println("seed-out: " + seedOut + "  → POST /api/v1/sports/replay/seed");
        }
        System.out.println("打开 Console 敞口看板: /girisk/exposure");
        System.out.println("=================================================");
    }

    private static String toConsoleSelection(String chinese) {
        if ("主胜".equals(chinese)) {
            return "home";
        }
        if ("平局".equals(chinese)) {
            return "draw";
        }
        if ("客胜".equals(chinese)) {
            return "away";
        }
        return null;
    }

    private static ScenarioExposure worst(ExposureSummary summary) {
        ScenarioExposure w = summary.scenarios.get(0);
        for (ScenarioExposure s : summary.scenarios) {
            if (s.bookmakerPnlCents < w.bookmakerPnlCents) {
                w = s;
            }
        }
        return w;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--") && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                m.put(a.substring(2), args[++i]);
            } else if (a.startsWith("--") && a.contains("=")) {
                int eq = a.indexOf('=');
                m.put(a.substring(2, eq), a.substring(eq + 1));
            }
        }
        return m;
    }
}
