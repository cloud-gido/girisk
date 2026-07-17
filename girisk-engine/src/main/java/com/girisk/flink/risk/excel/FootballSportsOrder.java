package com.girisk.flink.risk.excel;

import java.io.Serializable;

/** 足球单关投注订单（Kafka CSV 与结算逻辑共用）。 */
public final class FootballSportsOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 运营方 ID（BetConfirmedEvent 根字段 {@code operatorId}；CSV 无则为 0）。 */
    public long operatorId;

    /** 上游事件 ID（BetConfirmedEvent 根字段 {@code eventId}；CSV 无则空，下游幂等键回退 orderId）。 */
    public String eventId;

    /** 赛事 ID / fixture（CSV 第 1 列「赛事ID」）；按赛事维度汇总敞口。 */
    public String fixtureId;
    public String orderId;
    public String orderTime;
    public String userId;
    public String league;
    public String homeTeam;
    public String awayTeam;
    public String kickoffTime;
    /**
     * 玩法（支持多地区别名，结算时由 {@link com.girisk.flink.risk.settlement.PlayTypeRegistry} 识别）。
     *
     * <p>示例：胜平负、1X2、Match Result、大小球、Over/Under、让球胜平负、Asian Handicap
     */
    public String playType;
    /** 单关 / 串关等 */
    public String parlayType;
    public String handicapText;
    public String selection;
    public double odds;
    /** 投注金额（元，整数部分；小数本金请设 {@link #stakeCentsExact}）。 */
    public long stakeYuan;

    /**
     * 精确投注额（分）。{@code >= 0} 时 {@link #stakeCents()} 优先用该值，支持 Excel 小数本金回放。
     * 默认 {@code -1} 表示未设置，回退 {@code stakeYuan * 100}。
     */
    public long stakeCentsExact = -1L;

    public long stakeCents() {
        return stakeCentsExact >= 0L ? stakeCentsExact : stakeYuan * 100L;
    }

    public int oddsMilli() {
        return (int) Math.round(odds * 1000.0);
    }
}
