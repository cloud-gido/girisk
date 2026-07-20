package com.girisk.sports.dto;

/**
 * 本层门控覆盖。字段 null = 该开关继承上层（不写入本层）。
 */
public record ScopeGateOverrideRequest(
        Boolean tradingEnabled,
        Boolean limitGateEnabled,
        Boolean exposureGateEnabled,
        String operatorId
) {
}
