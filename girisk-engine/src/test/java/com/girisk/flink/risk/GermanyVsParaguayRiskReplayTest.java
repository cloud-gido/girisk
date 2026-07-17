package com.girisk.flink.risk;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.MatchExposureAggregator.ExposureSummary;
import com.girisk.flink.risk.grid.MatchExposureAggregator.ScenarioExposure;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.limit.MatchTriggerAcceptance;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 用 Germany vs Paraguay 真实订单流回放：对比「无风控全接」与「先限额后敞口」两道闸门后的平台最差亏损。
 *
 * <p>{@code mvn test -Dtest=GermanyVsParaguayRiskReplayTest}
 */
class GermanyVsParaguayRiskReplayTest {

    private static final double DELTA = 0.2;
    /** 与产品 HTML 本次回放一致：初始已投注金额=5000，风险阈值=200000。 */
    private static final double SEED = 5000.0;
    private static final double WORST_LOSS = 200_000.0;

    @Test
    void compareNoRiskVsTwoGates() throws Exception {
        List<FootballSportsOrder> orders = loadOrders();
        ScoreGridParams grid = ScoreGridParams.fromMap(Map.of("score", "0:0", "grid", "6"));

        // —— 无风控：按顺序全接 ——
        List<FootballSportsOrder> noRisk = new ArrayList<>(orders);
        ExposureSummary noRiskExposure = MatchExposureAggregator.summarize(noRisk, grid.grid);

        // —— 有风控：两道闸门，通过才入账（模拟接单后写 post CONFIRMED）——
        List<FootballSportsOrder> accepted = new ArrayList<>();
        int limitReject = 0;
        int exposureReject = 0;
        double acceptedStakeYuan = 0;
        double rejectedStakeYuan = 0;
        for (FootballSportsOrder order : orders) {
            EnrichedFootballOrder trigger =
                    new EnrichedFootballOrder(order, System.currentTimeMillis(), "germany-paraguay");
            MatchTriggerAcceptance decision =
                    MatchTriggerAcceptance.evaluate(
                            accepted,
                            trigger,
                            false,
                            grid.grid,
                            DELTA,
                            SEED,
                            WORST_LOSS,
                            true);
            double stakeExact = order.stakeCents() / 100.0;
            if (decision.rejectReason == MatchTriggerAcceptance.RejectReason.NONE) {
                accepted.add(order);
                acceptedStakeYuan += stakeExact;
            } else if (decision.rejectReason == MatchTriggerAcceptance.RejectReason.LIMIT) {
                limitReject++;
                rejectedStakeYuan += stakeExact;
            } else {
                exposureReject++;
                rejectedStakeYuan += stakeExact;
            }
        }
        ExposureSummary withRiskExposure = MatchExposureAggregator.summarize(accepted, grid.grid);

        double noRiskStake = noRisk.stream().mapToDouble(o -> o.stakeCents() / 100.0).sum();
        ScenarioExposure noRiskWorst = worst(noRiskExposure);
        ScenarioExposure withRiskWorst = worst(withRiskExposure);

        System.out.println();
        System.out.println("========== Germany vs Paraguay 风控回放 ==========");
        System.out.printf(Locale.ROOT, "订单总数: %d（全部胜平负）%n", orders.size());
        System.out.printf(
                Locale.ROOT,
                "参数: delta=%.1f seed=%.0f maxWorstLossYuan=%.0f（对齐产品 HTML 截图）%n",
                DELTA,
                SEED,
                WORST_LOSS);
        System.out.println();
        System.out.println("----- 无风控（全接）-----");
        System.out.printf(Locale.ROOT, "接单: %d 笔  本金合计: %.2f 元%n", noRisk.size(), noRiskStake);
        printOutcomeBuckets(noRisk);
        System.out.printf(
                Locale.ROOT,
                "最差平台净盈亏: %.2f 元  @比分 %d:%d（bookmakerPnl）%n",
                noRiskWorst.bookmakerPnlCents / 100.0,
                noRiskWorst.scenario.homeGoals,
                noRiskWorst.scenario.awayGoals);
        System.out.printf(
                Locale.ROOT,
                "最差净亏（正数口径 maxBookmakerLoss）: %.2f 元%n",
                noRiskExposure.maxBookmakerLossCents / 100.0);
        printThreeWayPnl(noRiskExposure);
        System.out.println();
        System.out.println("----- 有风控（Gate1 限额 + Gate2 敞口）-----");
        System.out.printf(
                Locale.ROOT,
                "接单: %d 笔  拒单: %d 笔（LIMIT=%d, EXPOSURE=%d）%n",
                accepted.size(),
                limitReject + exposureReject,
                limitReject,
                exposureReject);
        System.out.printf(
                Locale.ROOT,
                "接单本金: %.2f 元  拒单本金: %.2f 元  接单率: %.1f%%%n",
                acceptedStakeYuan,
                rejectedStakeYuan,
                100.0 * accepted.size() / orders.size());
        printOutcomeBuckets(accepted);
        System.out.printf(
                Locale.ROOT,
                "最差平台净盈亏: %.2f 元  @比分 %d:%d%n",
                withRiskWorst.bookmakerPnlCents / 100.0,
                withRiskWorst.scenario.homeGoals,
                withRiskWorst.scenario.awayGoals);
        System.out.printf(
                Locale.ROOT,
                "最差净亏（正数口径 maxBookmakerLoss）: %.2f 元%n",
                withRiskExposure.maxBookmakerLossCents / 100.0);
        printThreeWayPnl(withRiskExposure);
        System.out.println();
        double improve =
                (noRiskExposure.maxBookmakerLossCents - withRiskExposure.maxBookmakerLossCents)
                        / 100.0;
        double improvePct =
                noRiskExposure.maxBookmakerLossCents == 0
                        ? 0
                        : 100.0
                                * (noRiskExposure.maxBookmakerLossCents
                                        - withRiskExposure.maxBookmakerLossCents)
                                / noRiskExposure.maxBookmakerLossCents;
        System.out.println("----- 对比结论 -----");
        System.out.printf(
                Locale.ROOT,
                "最差净亏改善: %.2f 元（%.1f%%）  无风控 %.2f → 有风控 %.2f%n",
                improve,
                improvePct,
                noRiskExposure.maxBookmakerLossCents / 100.0,
                withRiskExposure.maxBookmakerLossCents / 100.0);
        System.out.println();
        System.out.println("----- 与产品 HTML 对齐检查 -----");
        System.out.println("产品截图: 接单1964 拦截767(LIMIT767/RISK0) 无风控-772040.26@0:1 有风控-19792.34@1:0 接单本金129893.96");
        System.out.printf(
                Locale.ROOT,
                "本次Flink: 接单%d 拦截%d(LIMIT%d/RISK%d) 无风控%.2f@%d:%d 有风控%.2f@%d:%d 接单本金%.2f%n",
                accepted.size(),
                limitReject + exposureReject,
                limitReject,
                exposureReject,
                noRiskWorst.bookmakerPnlCents / 100.0,
                noRiskWorst.scenario.homeGoals,
                noRiskWorst.scenario.awayGoals,
                withRiskWorst.bookmakerPnlCents / 100.0,
                withRiskWorst.scenario.homeGoals,
                withRiskWorst.scenario.awayGoals,
                acceptedStakeYuan);
        System.out.println("=================================================");
    }

    private static ScenarioExposure worst(ExposureSummary summary) {
        ScenarioExposure worst = summary.scenarios.get(0);
        for (ScenarioExposure s : summary.scenarios) {
            if (s.bookmakerPnlCents < worst.bookmakerPnlCents) {
                worst = s;
            }
        }
        return worst;
    }

    private static void printThreeWayPnl(ExposureSummary summary) {
        // 1X2 下同结果比分 PnL 相同，取代表格
        long home = pnlAt(summary, 1, 0);
        long draw = pnlAt(summary, 0, 0);
        long away = pnlAt(summary, 0, 1);
        System.out.printf(
                Locale.ROOT,
                "分结果庄家盈亏: 主胜(1:0)=%.2f  平局(0:0)=%.2f  客胜(0:1)=%.2f 元%n",
                home / 100.0,
                draw / 100.0,
                away / 100.0);
    }

    private static long pnlAt(ExposureSummary summary, int h, int a) {
        for (ScenarioExposure s : summary.scenarios) {
            if (s.scenario.homeGoals == h && s.scenario.awayGoals == a) {
                return s.bookmakerPnlCents;
            }
        }
        return 0L;
    }

    private static void printOutcomeBuckets(List<FootballSportsOrder> orders) {
        double homeS = 0, drawS = 0, awayS = 0;
        double homeP = 0, drawP = 0, awayP = 0;
        for (FootballSportsOrder o : orders) {
            double stake = o.stakeCents() / 100.0;
            double payout = stake * o.odds;
            if ("主胜".equals(o.selection)) {
                homeS += stake;
                homeP += payout;
            } else if ("平局".equals(o.selection)) {
                drawS += stake;
                drawP += payout;
            } else {
                awayS += stake;
                awayP += payout;
            }
        }
        System.out.printf(
                Locale.ROOT,
                "盘口本金/返彩: 主胜 %.0f/%.0f  平局 %.0f/%.0f  客胜 %.0f/%.0f%n",
                homeS,
                homeP,
                drawS,
                drawP,
                awayS,
                awayP);
    }

    private static List<FootballSportsOrder> loadOrders() throws Exception {
        List<FootballSportsOrder> out = new ArrayList<>();
        try (BufferedReader br =
                new BufferedReader(
                        new InputStreamReader(
                                Objects.requireNonNull(
                                        GermanyVsParaguayRiskReplayTest.class
                                                .getResourceAsStream(
                                                        "/germany-vs-paraguay-orders.csv")),
                                StandardCharsets.UTF_8))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] p = line.split(",", -1);
                FootballSportsOrder o = new FootballSportsOrder();
                o.fixtureId = "germany-paraguay";
                o.orderId = p[1];
                o.orderTime = "2026-06-01 12:00:00";
                o.league = "国际友谊";
                o.homeTeam = "Germany";
                o.awayTeam = "Paraguay";
                o.kickoffTime = "2026-06-01 20:00:00";
                o.playType = "胜平负";
                o.parlayType = "单关";
                o.handicapText = "无";
                o.selection = p[2];
                o.odds = Double.parseDouble(p[3]);
                double stake = Double.parseDouble(p[4]);
                o.stakeCentsExact = Math.round(stake * 100.0);
                o.stakeYuan = Math.max(1L, Math.round(stake));
                out.add(o);
            }
        }
        return out;
    }
}
