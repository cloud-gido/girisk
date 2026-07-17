package com.girisk.flink.risk.excel;

import com.girisk.flink.risk.settlement.BetSettlementEngine;

/**
 * 足球订单假设比分结算门面（兼容旧调用方）。
 *
 * <p>支持多地区玩法别名，见 {@link com.girisk.flink.risk.settlement.PlayTypeRegistry}。
 */
public final class FootballBetSettlement {

    private FootballBetSettlement() {}

    public static final class Settlement {
        public final BetResultLabel label;
        public final long bookmakerPnlCents;

        public Settlement(BetResultLabel label, long bookmakerPnlCents) {
            this.label = label;
            this.bookmakerPnlCents = bookmakerPnlCents;
        }
    }

    public static Settlement settle(FootballSportsOrder o, int homeGoals, int awayGoals) {
        return BetSettlementEngine.settle(o, homeGoals, awayGoals);
    }

    /** 用户盈利（分）：客赢为正、客输为负，与庄家 P&amp;L 符号相反。 */
    public static long userPnlCents(FootballSportsOrder o, int homeGoals, int awayGoals) {
        return -settle(o, homeGoals, awayGoals).bookmakerPnlCents;
    }

    /**
     * 用户在该假设比分下从平台实际可收回的金额（分）：<b>含退回本金</b>；全输为 0；走水为全额本金；欧赔全赢约为
     * {@code stake × odds} 对应的分值。
     *
     * <p>与 {@link #userPnlCents} 的关系：{@code max(0, stakeCents + userPnlCents)}（与当前 {@link
     * com.girisk.flink.risk.settlement.SettlementPnl} 模型一致）。
     */
    public static long userPayableCents(FootballSportsOrder o, int homeGoals, int awayGoals) {
        long pnl = userPnlCents(o, homeGoals, awayGoals);
        return Math.max(0L, o.stakeCents() + pnl);
    }
}
