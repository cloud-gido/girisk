package com.girisk.sports.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SportsBetEvaluateRequest(
        @NotBlank String orderId,
        @NotBlank String matchCode,
        @NotBlank String marketType,
        String line,
        @NotBlank String selection,
        @NotNull BigDecimal amount,
        BigDecimal odds,
        Boolean dryRun,
        /** 统一入口要求；旧调用方可仍缺，Controller 适配层会校验。 */
        String userId,
        String operatorId
) {
    public SportsBetEvaluateRequest(
            String orderId, String matchCode, String marketType, String line,
            String selection, BigDecimal amount, BigDecimal odds, Boolean dryRun) {
        this(orderId, matchCode, marketType, line, selection, amount, odds, dryRun, null, null);
    }
}
