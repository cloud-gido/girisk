package com.girisk.sports.model;

import com.girisk.common.exception.BusinessException;

/** 限额/门控层级：解析时赛前/滚球 > 单赛事 > 联赛 > 球类 > 默认(总体) > 系统默认。 */
public enum LimitScopeType {
    OVERALL,
    SPORT,
    LEAGUE,
    MATCH,
    /** 单场赛前限额覆盖（叠在 MATCH 之上） */
    MATCH_PRE,
    /** 单场滚球限额覆盖（叠在 MATCH 之上） */
    MATCH_LIVE;

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

    /** 值班写权限：与 MATCH 同级（需 duty:write_match）。 */
    public boolean isMatchLevel() {
        return this == MATCH || this == MATCH_PRE || this == MATCH_LIVE;
    }
}
