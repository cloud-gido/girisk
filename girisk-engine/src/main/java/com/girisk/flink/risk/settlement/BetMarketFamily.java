package com.girisk.flink.risk.settlement;

/** 订单玩法族（与地区展示名解耦，用于路由结算逻辑）。 */
public enum BetMarketFamily {
    /** 胜平负 / 1X2 / Match Result */
    MATCH_RESULT,
    /** 大小球 / Over-Under / Total Goals */
    OVER_UNDER,
    /** 亚洲让球（主/客两边，支持四分盘） */
    ASIAN_HANDICAP,
    /** 让球胜平负（让胜/让平/让负三选一） */
    HANDICAP_THREE_WAY,
    /** 无法识别 */
    UNKNOWN
}
