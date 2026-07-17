package com.girisk.flink.risk.settlement;

import com.girisk.flink.risk.excel.BetResultLabel;
import com.girisk.flink.risk.excel.FootballSportsOrder;

/** 亚洲让球（主/客两边，支持 0.25/0.75 四分盘半赢半输）。 */
public final class AsianHandicapSettlement {

    private AsianHandicapSettlement() {}

    public static BetResultLabel settle(FootballSportsOrder order, int homeGoals, int awayGoals) {
        boolean homeBet = SelectionNormalizer.isAsianHomeSide(order.selection);
        boolean awayBet = SelectionNormalizer.isAsianAwaySide(order.selection);
        if (homeBet && awayBet) {
            throw new IllegalArgumentException("亚洲让球投注项歧义: " + order.selection);
        }
        if (!homeBet && !awayBet) {
            throw new IllegalArgumentException("亚洲让球需指定主队或客队: " + order.selection);
        }

        HandicapLines.TeamLine teamLine =
                HandicapLines.parseTeamLine(order.handicapText, homeBet);
        double lineFromHome = teamLine.lineFromHomePerspective();
        int goalDiff = homeGoals - awayGoals;
        return AsianLineSettlement.settleHandicapMargin(goalDiff, lineFromHome, homeBet);
    }
}
