package com.girisk.sports.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** 场次有效限额参数（覆盖 ⊕ 赛事库 ⊕ 全局默认）及原始覆盖。 */
public record FixtureLimitParamsView(
        String matchCode,
        BigDecimal delta,
        BigDecimal seedPayoutYuan,
        BigDecimal maxWorstLossYuan,
        BigDecimal maxBetPayoutYuan,
        boolean overrideActive,
        BigDecimal baseDelta,
        BigDecimal baseExposureThreshold,
        BigDecimal globalSeedPayoutYuan,
        BigDecimal globalMaxWorstLossYuan,
        BigDecimal globalMaxBetPayoutYuan,
        BigDecimal overrideDelta,
        BigDecimal overrideSeedPayoutYuan,
        BigDecimal overrideMaxWorstLossYuan,
        BigDecimal overrideMaxBetPayoutYuan,
        String updatedBy,
        Instant updatedAt
) {}
