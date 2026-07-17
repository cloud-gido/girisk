package com.girisk.sports.dto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record SportsBetEvaluateResponse(
        String requestId,
        String orderId,
        String decision,
        String reason,
        boolean limitMode,
        BigDecimal amount,
        BigDecimal maxAcceptAmount,
        BigDecimal currentStake,
        BigDecimal groupTotal,
        BigDecimal targetAmount,
        BigDecimal maxAllowedAmount,
        Map<String, BigDecimal> groupStakes,
        Map<String, BigDecimal> groupLimits,
        Map<String, BigDecimal> groupTargets,
        Map<String, BigDecimal> groupMaxAllowed,
        BigDecimal matchExposure,
        BigDecimal exposureThreshold,
        long latencyMs
) {
    public static Map<String, BigDecimal> stakesMap(Map<String, BigDecimal> source) {
        return source == null ? Map.of() : new LinkedHashMap<>(source);
    }
}
