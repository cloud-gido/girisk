package com.girisk.flink.risk.settlement;

import com.girisk.flink.risk.excel.BetResultLabel;
import com.girisk.flink.risk.excel.FootballSportsOrder;

/** 根据输赢标签计算庄家 P&amp;L（分）。 */
public final class SettlementPnl {

    private SettlementPnl() {}

    public static long bookmakerPnlCents(FootballSportsOrder order, BetResultLabel label) {
        long stake = order.stakeCents();
        long profit = Math.round(stake * (order.odds - 1.0));
        switch (label) {
            case WIN:
                return -profit;
            case LOSE:
                return stake;
            case PUSH:
                return 0L;
            case WIN_HALF:
                return -profit / 2;
            case LOSE_HALF:
                return stake / 2;
            default:
                return stake;
        }
    }
}
