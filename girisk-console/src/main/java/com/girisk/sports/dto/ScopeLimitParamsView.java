package com.girisk.sports.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** 某层级有效限额参数 + 本层覆盖字段。 */
public record ScopeLimitParamsView(
        String scopeType,
        String scopeKey,
        BigDecimal delta,
        BigDecimal seedPayoutYuan,
        BigDecimal maxWorstLossYuan,
        BigDecimal maxBetPayoutYuan,
        boolean overrideActive,
        BigDecimal inheritedDelta,
        BigDecimal inheritedSeedPayoutYuan,
        BigDecimal inheritedMaxWorstLossYuan,
        BigDecimal inheritedMaxBetPayoutYuan,
        BigDecimal overrideDelta,
        BigDecimal overrideSeedPayoutYuan,
        BigDecimal overrideMaxWorstLossYuan,
        BigDecimal overrideMaxBetPayoutYuan,
        String updatedBy,
        Instant updatedAt
) {}
