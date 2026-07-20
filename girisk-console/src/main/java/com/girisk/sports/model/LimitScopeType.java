package com.girisk.sports.model;

import com.girisk.common.exception.BusinessException;

/** 限额/门控层级：解析时单赛事 > 联赛 > 球类 > 默认(总体) > 系统默认。 */
public enum LimitScopeType {
    OVERALL,
    SPORT,
    LEAGUE,
    MATCH;

    public static LimitScopeType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("scopeType required");
        }
        try {
            return LimitScopeType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("未知 scopeType: " + raw);
        }
    }
}
