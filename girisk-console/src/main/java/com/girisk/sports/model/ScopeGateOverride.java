package com.girisk.sports.model;

import java.time.Instant;

/**
 * 层级门控开关覆盖。字段 null = 该开关不覆盖（继承下层）。
 * scopeKey：OVERALL=_ ；SPORT=football ；LEAGUE=football:FRIENDLY ；MATCH=matchCode
 */
public record ScopeGateOverride(
        LimitScopeType scopeType,
        String scopeKey,
        Boolean tradingEnabled,
        Boolean limitGateEnabled,
        Boolean exposureGateEnabled,
        String updatedBy,
        Instant updatedAt
) {
    public boolean hasAny() {
        return tradingEnabled != null || limitGateEnabled != null || exposureGateEnabled != null;
    }

    public static String normalizeKey(LimitScopeType type, String scopeKey) {
        if (type == LimitScopeType.OVERALL) {
            return "_";
        }
        return scopeKey == null ? "" : scopeKey.trim();
    }
}
