package com.girisk.sports.model;

import com.girisk.common.exception.BusinessException;

/** 赛事限额 API 分段：整场 / 赛前 / 滚球。 */
public enum MatchLimitSegment {
    ALL,
    PRE,
    LIVE;

    public static MatchLimitSegment from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        return switch (raw.trim().toLowerCase()) {
            case "all", "match" -> ALL;
            case "pre", "prematch", "match_pre" -> PRE;
            case "live", "inplay", "match_live" -> LIVE;
            default -> throw new BusinessException("未知 segment: " + raw + "（all|pre|live）");
        };
    }

    public LimitScopeType scopeType() {
        return switch (this) {
            case ALL -> LimitScopeType.MATCH;
            case PRE -> LimitScopeType.MATCH_PRE;
            case LIVE -> LimitScopeType.MATCH_LIVE;
        };
    }
}
