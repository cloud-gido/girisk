package com.girisk.flink.risk;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.limit.ExposureLimitGate;
import com.girisk.flink.risk.limit.LimitSelectionResolver;
import com.girisk.flink.risk.limit.LimitSelectionResolver.ResolvedOutcome;
import com.girisk.flink.risk.limit.MarketStakeAggregator;
import com.girisk.flink.risk.limit.MarketStakeAggregator.MarketGroupLimit;
import com.girisk.flink.risk.limit.MarketStakeAggregator.OutcomeLimitRow;
import com.girisk.flink.risk.limit.MatchTriggerAcceptance;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对账前台 {@code bet_order.xlsx} 导出的 CSV（墨西哥 vs 南非，seed=5000，δ=0.2）。
 *
 * <pre>
 * # 先导出 CSV：
 * /tmp/xlsxvenv/bin/python ... → target/bet-order-replay/orders.csv
 * mvn -pl girisk-engine -am test -Dtest=BetOrderXlsxReplayCompareTest \
 *   -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
class BetOrderXlsxReplayCompareTest {

    private static final Path CSV =
            Path.of("target/bet-order-replay/orders.csv").toAbsolutePath().normalize();
    private static final Path CSV_FROM_ROOT =
            Path.of("../target/bet-order-replay/orders.csv").toAbsolutePath().normalize();

    private static final double DELTA = 0.2;
    private static final double SEED = 5000.0;
    private static final ScoreGridParams GRID =
            ScoreGridParams.fromMap(Map.of("score", "0:0", "grid", "6"));

    @Test
    void replayMatchesExcelAcceptRejectAndBMax() throws Exception {
        Path csv = Files.isRegularFile(CSV) ? CSV : CSV_FROM_ROOT;
        Assumptions.assumeTrue(Files.isRegularFile(csv), "missing " + csv);

        List<Row> rows = load(csv);
        List<FootballSportsOrder> accepted = new ArrayList<>();

        int decisionMismatch = 0;
        int bMaxMismatch = 0;
        int groupMismatch = 0;
        int stakeBeforeMismatch = 0;
        int compared = 0;
        StringBuilder samples = new StringBuilder();

        for (Row row : rows) {
            FootballSportsOrder order = toOrder(row);
            EnrichedFootballOrder trigger =
                    new EnrichedFootballOrder(order, System.currentTimeMillis(), order.fixtureId);

            // 与产品表一致：接单进入窗口；无敞口拒（表中 767 笔全是限额拦截）
            MatchTriggerAcceptance acc =
                    MatchTriggerAcceptance.evaluate(
                            accepted,
                            trigger,
                            false,
                            GRID.grid,
                            DELTA,
                            SEED,
                            ExposureLimitGate.WORST_LOSS_DISABLED,
                            false);

            boolean expectReject = "拦截".equals(row.expectedResult) || "是".equals(row.expectedLimitReject);
            boolean actualReject = acc.limitRejected || acc.exposureRejected;
            compared++;

            if (expectReject != actualReject) {
                decisionMismatch++;
                if (decisionMismatch <= 8) {
                    samples
                            .append(
                                    String.format(
                                            Locale.ROOT,
                                            "seq=%d expectReject=%s actual=%s limit=%s exp=%s occ=%s bMaxExp=%s%n",
                                            row.seq,
                                            expectReject,
                                            actualReject,
                                            acc.limitRejected,
                                            acc.exposureRejected,
                                            row.expectedOccupation,
                                            row.expectedAcceptMax))
                            .append('\n');
                }
            }

            // Gate1 现场数字（判断前窗口 = accepted）
            List<MarketGroupLimit> priorGroups =
                    MarketStakeAggregator.aggregate(accepted, DELTA, SEED, order);
            Optional<ResolvedOutcome> resolved = LimitSelectionResolver.resolve(order);
            if (resolved.isPresent()) {
                ResolvedOutcome r = resolved.get();
                String gk = LimitSelectionResolver.groupKey(r.marketType, r.line);
                MarketGroupLimit g = findGroup(priorGroups, gk);
                OutcomeLimitRow orow = g == null ? null : findOutcome(g, r.selectionKey);
                if (g != null && row.expectedGroupTotal != null) {
                    if (diff(g.groupTotalStake, row.expectedGroupTotal) > 0.02) {
                        groupMismatch++;
                        if (groupMismatch <= 5) {
                            samples.append(
                                    String.format(
                                            Locale.ROOT,
                                            "seq=%d groupTotal eng=%s xls=%s%n",
                                            row.seq,
                                            g.groupTotalStake,
                                            row.expectedGroupTotal));
                        }
                    }
                }
                if (orow != null && row.expectedSelectionStake != null) {
                    if (diff(orow.stake, row.expectedSelectionStake) > 0.02) {
                        stakeBeforeMismatch++;
                    }
                }
                if (orow != null && row.expectedAcceptMax != null) {
                    if (diff(orow.acceptMax, row.expectedAcceptMax) > 0.05) {
                        bMaxMismatch++;
                        if (bMaxMismatch <= 5) {
                            samples.append(
                                    String.format(
                                            Locale.ROOT,
                                            "seq=%d bMax eng=%s xls=%s occ=%s%n",
                                            row.seq,
                                            orow.acceptMax,
                                            row.expectedAcceptMax,
                                            MarketStakeAggregator.payoutYuan(order)));
                        }
                    }
                }
            }

            if (!actualReject) {
                accepted.add(order);
            }
        }

        System.out.printf(
                Locale.ROOT,
                "=== bet_order.xlsx replay ===%n"
                        + "orders=%d acceptedWindow=%d%n"
                        + "decisionMismatch=%d (%.2f%%)%n"
                        + "bMaxMismatch=%d groupMismatch=%d stakeBeforeMismatch=%d%n"
                        + "samples:%n%s%n",
                compared,
                accepted.size(),
                decisionMismatch,
                100.0 * decisionMismatch / compared,
                bMaxMismatch,
                groupMismatch,
                stakeBeforeMismatch,
                samples);

        // 决策一致率应接近 100%（产品表仅限额闸门）
        assertTrue(
                decisionMismatch == 0,
                "accept/reject mismatch count=" + decisionMismatch + " / " + compared);
        assertTrue(
                bMaxMismatch < compared * 0.01,
                "b_max mismatch too high: " + bMaxMismatch + " / " + compared);
    }

    private static FootballSportsOrder toOrder(Row row) {
        FootballSportsOrder o = new FootballSportsOrder();
        o.fixtureId = "mexico-south-africa";
        o.orderId = row.orderId;
        o.orderTime = "2026-06-01T12:00:00Z";
        o.league = "国际友谊";
        o.homeTeam = "墨西哥";
        o.awayTeam = "南非";
        o.kickoffTime = "2026-06-01T20:00:00Z";
        o.playType = "胜平负";
        o.parlayType = "单关";
        o.handicapText = "无";
        o.selection = row.selection;
        o.odds = row.odds;
        o.stakeCentsExact = Math.round(row.stakeYuan * 100.0);
        o.stakeYuan = Math.max(1L, Math.round(row.stakeYuan));
        o.userId = "demo";
        o.operatorId = 1L;
        o.eventId = "seq-" + row.seq;
        return o;
    }

    private static List<Row> load(Path csv) throws Exception {
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        List<Row> out = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            String[] p = line.split(",", -1);
            Row r = new Row();
            r.seq = Integer.parseInt(p[0].trim());
            r.orderId = p[1].trim();
            r.selection = p[2].trim();
            r.odds = Double.parseDouble(p[3].trim());
            r.stakeYuan = Double.parseDouble(p[4].trim());
            r.expectedResult = p[5].trim();
            r.expectedLimitReject = p[6].trim();
            r.expectedAcceptMax = parseBd(p[7]);
            r.expectedOccupation = parseBd(p[8]);
            r.expectedGroupTotal = parseBd(p[9]);
            r.expectedSelectionStake = parseBd(p[10]);
            out.add(r);
        }
        return out;
    }

    private static BigDecimal parseBd(String s) {
        if (s == null || s.isBlank() || "None".equals(s) || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return new BigDecimal(s.trim()).setScale(6, RoundingMode.HALF_UP);
    }

    private static double diff(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().doubleValue();
    }

    private static MarketGroupLimit findGroup(List<MarketGroupLimit> groups, String groupKey) {
        for (MarketGroupLimit g : groups) {
            if (groupKey.equals(g.groupKey)) {
                return g;
            }
        }
        return null;
    }

    private static OutcomeLimitRow findOutcome(MarketGroupLimit g, String selection) {
        for (OutcomeLimitRow row : g.rows) {
            if (selection.equals(row.selection)) {
                return row;
            }
        }
        return null;
    }

    private static final class Row {
        int seq;
        String orderId;
        String selection;
        double odds;
        double stakeYuan;
        String expectedResult;
        String expectedLimitReject;
        BigDecimal expectedAcceptMax;
        BigDecimal expectedOccupation;
        BigDecimal expectedGroupTotal;
        BigDecimal expectedSelectionStake;
    }
}
