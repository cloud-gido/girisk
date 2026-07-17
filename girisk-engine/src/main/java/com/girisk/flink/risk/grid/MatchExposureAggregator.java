package com.girisk.flink.risk.grid;

import com.girisk.flink.risk.excel.FootballBetSettlement;
import com.girisk.flink.risk.excel.FootballSportsOrder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 按假设比分网格汇总一场比赛中多笔未结算订单的庄家 P&amp;L。 */
public final class MatchExposureAggregator {

    public static final class ScenarioExposure {
        public final ScoreGridSpec.ScoreScenario scenario;
        /** 窗口内多笔订单在该假设比分下庄家 P&amp;L 合计（分）。 */
        public final long bookmakerPnlCents;
        /** 窗口内投注本金合计（分）。 */
        public final long stakeSumCents;
        /** 窗口内多笔订单在该假设比分下平台应付合计（分）。 */
        public final long platformPayableSumCents;

        public ScenarioExposure(
                ScoreGridSpec.ScoreScenario scenario,
                long bookmakerPnlCents,
                long stakeSumCents,
                long platformPayableSumCents) {
            this.scenario = scenario;
            this.bookmakerPnlCents = bookmakerPnlCents;
            this.stakeSumCents = stakeSumCents;
            this.platformPayableSumCents = platformPayableSumCents;
        }

        /** 平台净支出 = 应付 − 已收本金（分）；与 {@code platformPayableSumCents - stakeSumCents} 一致。 */
        public long profitCents() {
            return platformPayableSumCents - stakeSumCents;
        }
    }

    public static final class ExposureSummary {
        public final List<ScenarioExposure> scenarios;
        public final long maxBookmakerLossCents;

        public ExposureSummary(List<ScenarioExposure> scenarios, long maxBookmakerLossCents) {
            this.scenarios = scenarios;
            this.maxBookmakerLossCents = maxBookmakerLossCents;
        }
    }

    private MatchExposureAggregator() {}

    public static ExposureSummary summarize(List<FootballSportsOrder> openOrders, ScoreGridSpec grid) {
        List<ScenarioExposure> list = new ArrayList<>(grid.scenarioCount());
        long worstBookmakerPnl = Long.MAX_VALUE;
        long stakeSum = 0L;
        for (FootballSportsOrder o : openOrders) {
            stakeSum += o.stakeCents();
        }
        for (ScoreGridSpec.ScoreScenario sc : grid.scenarios()) {
            long bookmakerSum = 0L;
            long payableSum = 0L;
            for (FootballSportsOrder o : openOrders) {
                bookmakerSum +=
                        FootballBetSettlement.settle(o, sc.homeGoals, sc.awayGoals).bookmakerPnlCents;
                payableSum +=
                        FootballBetSettlement.userPayableCents(o, sc.homeGoals, sc.awayGoals);
            }
            list.add(new ScenarioExposure(sc, bookmakerSum, stakeSum, payableSum));
            worstBookmakerPnl = Math.min(worstBookmakerPnl, bookmakerSum);
        }
        long maxLoss = worstBookmakerPnl < 0 ? -worstBookmakerPnl : 0L;
        return new ExposureSummary(list, maxLoss);
    }

    public static List<ScenarioExposure> topLossScenarios(ExposureSummary summary, int topN) {
        List<ScenarioExposure> copy = new ArrayList<>(summary.scenarios);
        copy.sort(Comparator.comparingLong(s -> s.bookmakerPnlCents));
        if (copy.size() <= topN) {
            return copy;
        }
        return new ArrayList<>(copy.subList(0, topN));
    }

    /** 窗口内庄家 P&amp;L 最差的假设比分（用于风险 Kafka 一条汇总）。 */
    public static ScenarioExposure worstScenario(ExposureSummary summary) {
        ScenarioExposure worst = null;
        for (ScenarioExposure se : summary.scenarios) {
            if (worst == null || se.bookmakerPnlCents < worst.bookmakerPnlCents) {
                worst = se;
            }
        }
        return worst;
    }

    public static String formatTopLossLine(int rank, ScenarioExposure row) {
        long loss = row.bookmakerPnlCents < 0 ? -row.bookmakerPnlCents : 0L;
        return String.format(
                Locale.ROOT,
                "[比分TOP亏损] #%d 比分 %s 庄家P&L(分)=%d 亏损(分)=%d",
                rank,
                row.scenario.scoreLabel(),
                row.bookmakerPnlCents,
                loss);
    }
}
