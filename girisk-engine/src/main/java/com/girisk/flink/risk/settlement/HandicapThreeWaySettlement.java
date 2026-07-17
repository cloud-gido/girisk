package com.girisk.flink.risk.settlement;

import com.girisk.flink.risk.excel.BetResultLabel;
import com.girisk.flink.risk.excel.FootballSportsOrder;

/**
 * 让球胜平负（三选一）。整数盘按调整后比分；0.25/0.75 等四分盘走亚洲让球拆线规则（半赢半输）。
 */
public final class HandicapThreeWaySettlement {

    private HandicapThreeWaySettlement() {}

    public static BetResultLabel settle(FootballSportsOrder order, int homeGoals, int awayGoals) {
        boolean homeBet = SelectionNormalizer.isHandicapHomeWin(order.selection);
        boolean awayBet = SelectionNormalizer.isHandicapAwayWin(order.selection);
        boolean drawBet = SelectionNormalizer.isHandicapDraw(order.selection);
        if (!homeBet && !awayBet && !drawBet) {
            throw new IllegalArgumentException("让球胜平负投注项无法识别: " + order.selection);
        }

        HandicapLines.TeamLine teamLine =
                HandicapLines.parseTeamLine(
                        order.handicapText, homeBet || drawBet || !awayBet);

        double lineFromHome = teamLine.lineFromHomePerspective();
        if (AsianLineSettlement.isQuarterLine(lineFromHome)
                || !AsianLineSettlement.isIntegerLine(lineFromHome)) {
            int goalDiff = homeGoals - awayGoals;
            boolean backingHome = homeBet && !awayBet;
            if (awayBet && !homeBet) {
                backingHome = false;
            }
            if (drawBet && !homeBet && !awayBet) {
                backingHome = teamLine.side != HandicapLines.Side.AWAY;
            }
            return AsianLineSettlement.settleHandicapMargin(goalDiff, lineFromHome, backingHome);
        }

        double adjHome = homeGoals;
        double adjAway = awayGoals;
        if (teamLine.side == HandicapLines.Side.HOME) {
            adjHome += teamLine.line;
        } else if (teamLine.side == HandicapLines.Side.AWAY) {
            adjAway += teamLine.line;
        } else {
            double line = teamLine.lineFromHomePerspective();
            adjHome += line;
        }

        if (adjHome > adjAway) {
            return homeBet ? BetResultLabel.WIN : BetResultLabel.LOSE;
        }
        if (adjHome < adjAway) {
            return awayBet ? BetResultLabel.WIN : BetResultLabel.LOSE;
        }
        return drawBet ? BetResultLabel.WIN : BetResultLabel.LOSE;
    }
}
