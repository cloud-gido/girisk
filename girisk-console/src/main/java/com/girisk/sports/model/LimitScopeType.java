package com.girisk.sports.model;

import com.girisk.common.exception.BusinessException;

/** 限额覆盖层级：解析时赛事 > 联赛 > 球类 > 总体 > 全局默认。 */
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
