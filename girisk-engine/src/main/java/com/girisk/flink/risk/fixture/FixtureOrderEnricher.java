package com.girisk.flink.risk.fixture;

import com.girisk.flink.risk.excel.FootballSportsOrder;

/** 用赛事维表补全订单上的联赛 / 主客 / 开赛时间。 */
public final class FixtureOrderEnricher {

    private FixtureOrderEnricher() {}

    public static boolean needsFixtureDimension(FootballSportsOrder order) {
        return blank(order.league)
                || blank(order.homeTeam)
                || blank(order.awayTeam)
                || blank(order.kickoffTime);
    }

    public static void apply(FootballSportsOrder order, FixtureMetadata metadata) {
        order.league = metadata.league;
        order.homeTeam = metadata.homeTeam;
        order.awayTeam = metadata.awayTeam;
        order.kickoffTime = metadata.kickoffTime;
    }

    /** 维表有完整记录则补全；否则保持订单原值（JSON 多为空字符串）。 */
    public static void enrichIfPresent(FootballSportsOrder order, FixtureMetadataLookup lookup) {
        if (!needsFixtureDimension(order)) {
            return;
        }
        lookup.find(order.fixtureId)
                .filter(FixtureMetadata::isComplete)
                .ifPresent(meta -> apply(order, meta));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
