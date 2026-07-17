package com.girisk.sports.dto;

import java.math.BigDecimal;

/** 场次限额覆盖写入；字段省略或 null = 清除该项覆盖（继承下层）。 */
public record FixtureLimitOverrideRequest(
        BigDecimal delta,
        BigDecimal seedPayoutYuan,
        BigDecimal maxWorstLossYuan,
        BigDecimal maxBetPayoutYuan,
        String operatorId
) {}
