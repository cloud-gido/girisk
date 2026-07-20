package com.girisk.flink.risk.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.sink.legacy.RichSinkFunction;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Writes Flink summary snapshots into Redis materialised views for the console dashboard.
 * Keys: girisk:view:fixture:{id}, girisk:view:top:worstloss.
 */
public final class RedisFixtureViewSink extends RichSinkFunction<String> {

    private static final long serialVersionUID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String host;
    private final int port;
    private final String password;

    private transient JedisPool pool;

    public RedisFixtureViewSink(String host, int port, String password) {
        this.host = host == null || host.isBlank() ? "127.0.0.1" : host;
        this.port = port <= 0 ? 6379 : port;
        this.password = password;
    }

    @Override
    public void open(OpenContext openContext) {
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(8);
        if (password != null && !password.isBlank()) {
            pool = new JedisPool(cfg, host, port, 2000, password);
        } else {
            pool = new JedisPool(cfg, host, port, 2000);
        }
    }

    @Override
    public void invoke(String value, Context context) throws Exception {
        if (value == null || value.isBlank() || pool == null) {
            return;
        }
        JsonNode root = MAPPER.readTree(value);
        String fixtureId = text(root, "fixtureId");
        if (fixtureId.isEmpty()) {
            fixtureId = text(root.path("match"), "fixtureId");
        }
        if (fixtureId.isEmpty()) {
            return;
        }
        // Summary v8：最差敞口多用 maxProfitYuan（平台应付侧）；兼容 worstLossYuan
        long worstLossCents = yuanToCents(root.path("worstLossYuan").asDouble(0));
        if (worstLossCents == 0) {
            worstLossCents = Math.abs(root.path("maxProfitCents").asLong(0));
        }
        if (worstLossCents == 0) {
            worstLossCents = yuanToCents(Math.abs(root.path("maxProfitYuan").asDouble(0)));
        }
        if (worstLossCents == 0 && root.has("exposure")) {
            worstLossCents = yuanToCents(root.path("exposure").path("worstLossYuan").asDouble(0));
        }
        String worstScore = text(root, "worstScore");
        if (worstScore.isEmpty()) {
            worstScore = text(root, "maxProfitScore");
        }
        if (worstScore.isEmpty() && root.path("maxProfitScores").isArray()
                && root.path("maxProfitScores").size() > 0) {
            worstScore = root.path("maxProfitScores").get(0).asText("");
        }
        if (worstScore.isEmpty() && root.has("exposure")) {
            worstScore = text(root.path("exposure"), "worstScore");
        }
        int confirmed = root.path("windowOrderCount").asInt(root.path("confirmedOrderCount").asInt(0));
        String liveScore = text(root, "liveScore");
        long updatedAt = root.path("publishedAtMs").asLong(System.currentTimeMillis());
        String homeTeam = text(root, "homeTeam");
        String awayTeam = text(root, "awayTeam");

        double windowStakeYuan = root.path("windowStakeYuan").asDouble(
                root.path("windowStakeCents").asLong(0) / 100.0);
        // 平台最差应付 → 庄家最差盈亏（负）
        double withRiskWorstPnlYuan = -Math.abs(worstLossCents) / 100.0;
        Double noRiskWorstPnlYuan =
                root.has("noRiskWorstPnlYuan") && !root.path("noRiskWorstPnlYuan").isNull()
                        ? root.path("noRiskWorstPnlYuan").asDouble()
                        : null;
        String noRiskWorstScore = text(root, "noRiskWorstScore");

        try (Jedis jedis = pool.getResource()) {
            String key = "girisk:view:fixture:" + fixtureId;
            jedis.hset(key, "fixtureId", fixtureId);
            if (!homeTeam.isEmpty()) {
                jedis.hset(key, "homeTeam", homeTeam);
            }
            if (!awayTeam.isEmpty()) {
                jedis.hset(key, "awayTeam", awayTeam);
            }
            jedis.hset(key, "worstLossCents", String.valueOf(Math.abs(worstLossCents)));
            jedis.hset(key, "worstScore", worstScore);
            jedis.hset(key, "confirmedOrders", String.valueOf(confirmed));
            jedis.hset(key, "liveScore", liveScore);
            jedis.hset(key, "updatedAt", String.valueOf(updatedAt));
            jedis.hset(key, "rawSnapshot", value.length() > 4000 ? value.substring(0, 4000) : value);
            // 与决策累加器共用 replayStats：同步「拦截后」窗口事实（不覆盖拒单计数）
            mergeWindowIntoReplayStats(
                    jedis,
                    key,
                    confirmed,
                    windowStakeYuan,
                    withRiskWorstPnlYuan,
                    worstScore,
                    noRiskWorstPnlYuan,
                    noRiskWorstScore);
            jedis.expire(key, 7 * 24 * 3600);
            // 即使亏损为 0 也进榜，便于联调发现场次
            jedis.zadd("girisk:view:top:worstloss", Math.abs(worstLossCents), fixtureId);
        }
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }

    private static void mergeWindowIntoReplayStats(
            Jedis jedis,
            String key,
            int acceptedCount,
            double acceptedStakeYuan,
            double withRiskWorstPnlYuan,
            String withRiskWorstScore,
            Double noRiskWorstPnlYuan,
            String noRiskWorstScore)
            throws Exception {
        ObjectNode stats;
        String raw = jedis.hget(key, "replayStats");
        if (raw == null || raw.isBlank()) {
            stats = MAPPER.createObjectNode();
        } else {
            JsonNode n = MAPPER.readTree(raw);
            stats = n instanceof ObjectNode ? (ObjectNode) n : MAPPER.createObjectNode();
        }
        stats.put("acceptedCount", acceptedCount);
        stats.put("acceptedStakeYuan", Math.round(acceptedStakeYuan * 100.0) / 100.0);
        stats.put("withRiskWorstPnlYuan", Math.round(withRiskWorstPnlYuan * 100.0) / 100.0);
        if (withRiskWorstScore != null && !withRiskWorstScore.isBlank()) {
            stats.put("withRiskWorstScore", withRiskWorstScore);
        }
        if (noRiskWorstPnlYuan != null) {
            stats.put("noRiskWorstPnlYuan", Math.round(noRiskWorstPnlYuan * 100.0) / 100.0);
        }
        if (noRiskWorstScore != null && !noRiskWorstScore.isBlank()) {
            stats.put("noRiskWorstScore", noRiskWorstScore);
        }
        // 有效订单口径：接收 + 拦截 + 重复（覆盖，避免 decision 条数与窗口漂移）
        long rejected = stats.path("rejectedTotal").asLong(0);
        long duplicate = stats.path("duplicateCount").asLong(0);
        stats.put("totalOrders", acceptedCount + rejected + duplicate);
        jedis.hset(key, "replayStats", MAPPER.writeValueAsString(stats));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }

    private static long yuanToCents(double yuan) {
        return Math.round(yuan * 100.0);
    }
}
