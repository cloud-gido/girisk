package com.girisk.sports.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 任意层级限额覆盖。字段 null = 该参数不覆盖（继承下层）。
 * scopeKey：OVERALL=_ ；SPORT=football ；LEAGUE=football:FRIENDLY ；
 * MATCH / MATCH_PRE / MATCH_LIVE = matchCode
 */
public record ScopeLimitOverride(
        LimitScopeType scopeType,
        String scopeKey,
        BigDecimal delta,
        BigDecimal seedPayoutYuan,
        BigDecimal maxWorstLossYuan,
        BigDecimal maxBetPayoutYuan,
        String updatedBy,
        Instant updatedAt
) {
    public boolean hasAny() {
        return delta != null
                || seedPayoutYuan != null
                || maxWorstLossYuan != null
                || maxBetPayoutYuan != null;
    }

    public static String leagueKey(String sportCode, String leagueCode) {
        String s = sportCode == null || sportCode.isBlank() ? "football" : sportCode;
        String l = leagueCode == null || leagueCode.isBlank() ? "UNKNOWN" : leagueCode;
        return s + ":" + l;
    }

    /** @return [sportCode, leagueCode] */
    public static String[] splitLeagueKey(String scopeKey) {
        if (scopeKey == null || scopeKey.isBlank()) {
            return new String[]{"football", "UNKNOWN"};
        }
        int i = scopeKey.indexOf(':');
        if (i <= 0 || i >= scopeKey.length() - 1) {
            return new String[]{"football", scopeKey.trim()};
        }
        return new String[]{scopeKey.substring(0, i).trim(), scopeKey.substring(i + 1).trim()};
    }
}
