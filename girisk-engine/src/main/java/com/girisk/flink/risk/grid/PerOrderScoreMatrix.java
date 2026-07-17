package com.girisk.flink.risk.grid;

import com.girisk.flink.risk.excel.BetResultLabel;
import com.girisk.flink.risk.excel.FootballBetSettlement;
import com.girisk.flink.risk.excel.FootballSportsOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 单笔订单在指定假设比分网格下的用户盈利展开。 */
public final class PerOrderScoreMatrix {

    public static final class ScenarioLine {
        public final int index;
        public final ScoreGridSpec.ScoreScenario scenario;
        public final BetResultLabel result;
        public final long userPnlCents;

        public ScenarioLine(
                int index, ScoreGridSpec.ScoreScenario scenario, BetResultLabel result, long userPnlCents) {
            this.index = index;
            this.scenario = scenario;
            this.result = result;
            this.userPnlCents = userPnlCents;
        }
    }

    private PerOrderScoreMatrix() {}

    public static List<ScenarioLine> expand(FootballSportsOrder order, ScoreGridSpec grid) {
        List<ScenarioLine> lines = new ArrayList<>(grid.scenarioCount());
        int idx = 1;
        for (ScoreGridSpec.ScoreScenario sc : grid.scenarios()) {
            BetResultLabel label =
                    FootballBetSettlement.settle(order, sc.homeGoals, sc.awayGoals).label;
            long userPnl = FootballBetSettlement.userPnlCents(order, sc.homeGoals, sc.awayGoals);
            lines.add(new ScenarioLine(idx++, sc, label, userPnl));
        }
        return lines;
    }

    public static List<ScenarioLine> expand(FootballSportsOrder order) {
        return expand(order, ScoreGridSpec.xlsxDefault6x6());
    }

    public static List<String> formatOrderHeader(
            int orderSeq, int orderTotal, FootballSportsOrder order, ScoreGridParams params) {
        List<String> h = new ArrayList<>(2);
        h.add(
                String.format(
                        Locale.ROOT,
                        "======== 订单 %d/%d %s | %s %s %s | 赔率%.2f 金额%d元 | 当前比分%d:%d 假设%s（%d×%d=%d条）========",
                        orderSeq,
                        orderTotal,
                        order.orderId,
                        order.playType,
                        nullToDash(order.handicapText),
                        order.selection,
                        order.odds,
                        order.stakeYuan,
                        params.baseHome,
                        params.baseAway,
                        params.grid.rangeLabel(),
                        params.grid.homeSpan(),
                        params.grid.awaySpan(),
                        params.grid.scenarioCount()));
        h.add("序号\t假设比分\t输赢\t用户盈利(元)");
        return h;
    }

    public static String formatScenarioLine(ScenarioLine line) {
        return String.format(
                Locale.ROOT,
                "%d\t%s\t%s\t%.2f",
                line.index,
                line.scenario.scoreLabel(),
                line.result.display,
                line.userPnlCents / 100.0);
    }

    private static String nullToDash(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }
}
