package com.girisk.common.dto;

import com.girisk.common.enums.RiskDecision;
import com.girisk.common.enums.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RiskEvaluateRequest(
        @NotBlank String orderId,
        @NotBlank String userId,
        @NotNull BigDecimal amount,
        String currency,
        String paymentMethod,
        String ip,
        String deviceId,
        String merchantId,
        String productCategory,
        String country,
        Integer orderCount24h,
        BigDecimal amountSum24h,
        Boolean isNewUser,
        Integer deviceRiskScore,
        String scenario
) {
    public String scenarioOrDefault() {
        return scenario != null && !scenario.isBlank() ? scenario : "POST_ORDER";
    }
}
