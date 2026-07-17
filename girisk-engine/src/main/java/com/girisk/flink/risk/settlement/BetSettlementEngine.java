package com.girisk.flink.risk.settlement;

import com.girisk.flink.risk.excel.BetResultLabel;
import com.girisk.flink.risk.excel.FootballBetSettlement;
import com.girisk.flink.risk.excel.FootballSportsOrder;

/**
 * 多地区玩法结算入口：别名识别 + 分族结算。
 *
 * <p>对外仍推荐通过 {@link FootballBetSettlement} 调用，以保持兼容。
 */
public final class BetSettlementEngine {

    private BetSettlementEngine() {}

    public static BetResultLabel resultLabel(FootballSportsOrder order, int homeGoals, int awayGoals) {
        BetMarketFamily family = PlayTypeRegistry.resolve(order);
        try {
            switch (family) {
                case MATCH_RESULT:
                    return MatchResultSettlement.settle(order, homeGoals, awayGoals);
                case OVER_UNDER:
                    return OverUnderSettlement.settle(order, homeGoals, awayGoals);
                case ASIAN_HANDICAP:
                    return AsianHandicapSettlement.settle(order, homeGoals, awayGoals);
                case HANDICAP_THREE_WAY:
                    return HandicapThreeWaySettlement.settle(order, homeGoals, awayGoals);
                case UNKNOWN:
                default:
                    return settleUnknown(order, homeGoals, awayGoals);
            }
        } catch (IllegalArgumentException ex) {
            System.err.printf(
                    "[结算] 订单 %s 玩法=%s 盘口=%s 选项=%s 解析失败: %s%n",
                    safe(order.orderId),
                    safe(order.playType),
                    safe(order.handicapText),
                    safe(order.selection),
                    ex.getMessage());
            return BetResultLabel.LOSE;
        }
    }

    public static FootballBetSettlement.Settlement settle(
            FootballSportsOrder order, int homeGoals, int awayGoals) {
        BetResultLabel label = resultLabel(order, homeGoals, awayGoals);
        long pnl = SettlementPnl.bookmakerPnlCents(order, label);
        return new FootballBetSettlement.Settlement(label, pnl);
    }

    private static BetResultLabel settleUnknown(
            FootballSportsOrder order, int homeGoals, int awayGoals) {
        System.err.printf(
                "[结算] 未识别玩法，按输处理: orderId=%s playType=%s selection=%s%n",
                safe(order.orderId),
                safe(order.playType),
                safe(order.selection));
        return BetResultLabel.LOSE;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
