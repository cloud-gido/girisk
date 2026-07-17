package com.girisk.flink.risk.model;

import com.girisk.flink.risk.excel.FootballSportsOrder;

import java.io.Serializable;

/** 带事件时间与场次键的订单（用于 Flink EventTime）。 */
public final class EnrichedFootballOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    public FootballSportsOrder order;
    /** 下单事件时间（毫秒） */
    public long orderTimeMs;
    /** 场次键：fixtureId|联赛|主队|客队|开赛时间 */
    public String matchKey;

    public EnrichedFootballOrder() {}

    public EnrichedFootballOrder(FootballSportsOrder order, long orderTimeMs, String matchKey) {
        this.order = order;
        this.orderTimeMs = orderTimeMs;
        this.matchKey = matchKey;
    }
}
