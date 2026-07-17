package com.girisk.gateway;

import com.girisk.common.dto.RiskEvaluateRequest;
import com.girisk.common.exception.BusinessException;
import com.girisk.sports.dto.SportsBetEvaluateRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * 统一风控决策请求：用户规则段 + 体育限额/敞口段共用同一模型。
 */
public record RiskDecisionRequest(
        @NotBlank String traceId,
        @NotBlank String orderId,
        @NotBlank String userId,
        @NotBlank String operatorId,
        @NotNull Long stakeCents,
        String scenario,
        String currency,
        String paymentMethod,
        String ip,
        String deviceId,
        String productCategory,
        String country,
        Integer orderCount24h,
        BigDecimal amountSum24h,
        Boolean isNewUser,
        Integer deviceRiskScore,
        // 体育段（可选；有 matchCode/marketType/selection 则跑限额）
        String matchCode,
        String fixtureId,
        String marketType,
        String line,
        String selection,
        BigDecimal odds,
        Boolean dryRun,
        Boolean skipReserve
) {
    public String scenarioOrDefault() {
        if (scenario != null && !scenario.isBlank()) {
            return scenario;
        }
        return hasSportsMarket() ? "SPORTS_BET" : "POST_ORDER";
    }

    public boolean hasSportsMarket() {
        return matchCode != null && !matchCode.isBlank()
                && marketType != null && !marketType.isBlank()
                && selection != null && !selection.isBlank();
    }

    public String fixtureOrMatch() {
        if (fixtureId != null && !fixtureId.isBlank()) {
            return fixtureId;
        }
        return matchCode;
    }

    public BigDecimal amountYuan() {
        return BigDecimal.valueOf(stakeCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public static RiskDecisionRequest fromLegacy(RiskEvaluateRequest req) {
        long cents = req.amount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        String operator = req.merchantId() != null && !req.merchantId().isBlank() ? req.merchantId() : "DEFAULT";
        return new RiskDecisionRequest(
                "tr-" + UUID.randomUUID().toString().substring(0, 8),
                req.orderId(),
                req.userId(),
                operator,
                cents,
                req.scenarioOrDefault(),
                req.currency(),
                req.paymentMethod(),
                req.ip(),
                req.deviceId(),
                req.productCategory(),
                req.country(),
                req.orderCount24h(),
                req.amountSum24h(),
                req.isNewUser(),
                req.deviceRiskScore(),
                null, null, null, null, null, null, false, false);
    }

    public static RiskDecisionRequest fromSports(SportsBetEvaluateRequest req, String userId, String operatorId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException("体育投注必须提供 userId");
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new BusinessException("体育投注必须提供 operatorId");
        }
        long cents = req.amount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        return new RiskDecisionRequest(
                "tr-" + UUID.randomUUID().toString().substring(0, 8),
                req.orderId(),
                userId,
                operatorId,
                cents,
                "SPORTS_BET",
                null, null, null, null, null, null, null, null, null, null,
                req.matchCode(),
                req.matchCode(),
                req.marketType(),
                req.line(),
                req.selection(),
                req.odds(),
                req.dryRun(),
                false);
    }

    public RiskEvaluateRequest toUserEvaluateRequest() {
        return new RiskEvaluateRequest(
                orderId,
                userId,
                amountYuan(),
                currency,
                paymentMethod,
                ip,
                deviceId,
                operatorId,
                productCategory,
                country,
                orderCount24h,
                amountSum24h,
                isNewUser,
                deviceRiskScore,
                scenarioOrDefault());
    }

    public SportsBetEvaluateRequest toSportsRequest() {
        return new SportsBetEvaluateRequest(
                orderId,
                matchCode,
                marketType,
                line,
                selection,
                amountYuan(),
                odds,
                dryRun);
    }
}
