package com.girisk.flink.risk.settlement;

import com.girisk.flink.risk.excel.BetResultLabel;
import com.girisk.flink.risk.excel.FootballSportsOrder;

/** 胜平负 / 1X2 / Match Result。 */
public final class MatchResultSettlement {

    private MatchResultSettlement() {}

    public static BetResultLabel settle(FootballSportsOrder order, int homeGoals, int awayGoals) {
        if (homeGoals > awayGoals) {
            return SelectionNormalizer.isHomeWin(order.selection)
                    ? BetResultLabel.WIN
                    : BetResultLabel.LOSE;
        }
        if (homeGoals < awayGoals) {
            return SelectionNormalizer.isAwayWin(order.selection)
                    ? BetResultLabel.WIN
                    : BetResultLabel.LOSE;
        }
        return SelectionNormalizer.isDraw(order.selection) ? BetResultLabel.WIN : BetResultLabel.LOSE;
    }
}
