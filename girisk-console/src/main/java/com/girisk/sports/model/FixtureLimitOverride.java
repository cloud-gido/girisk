package com.girisk.sports.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 交易员场次级限额覆盖：叠在赛事库 / 全局默认之上，秒级生效。
 * 字段为 null 表示该参数不覆盖（继承下层）。
 */
public record FixtureLimitOverride(
        String matchCode,
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
}
