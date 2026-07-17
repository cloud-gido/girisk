package com.girisk.flink.risk.model;

import com.girisk.flink.risk.excel.FootballSportsOrder;

/** 场次维度键（同一比赛未结算订单窗口）。 */
public final class MatchKeys {

    private MatchKeys() {}

    public static String of(FootballSportsOrder order) {
        return String.join(
                "|",
                nullToEmpty(order.fixtureId),
                nullToEmpty(order.league),
                nullToEmpty(order.homeTeam),
                nullToEmpty(order.awayTeam),
                nullToEmpty(order.kickoffTime));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
