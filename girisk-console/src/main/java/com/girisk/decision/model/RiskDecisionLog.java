package com.girisk.decision.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RiskDecisionLog(
        Long id,
        String requestId,
        String orderId,
        String userId,
        String scenario,
        String strategyCode,
        String decision,
        int riskScore,
        String riskLevel,
        String hitRules,
        String reason,
        BigDecimal amount,
        String ip,
        String deviceId,
        Integer latencyMs,
        String source,
        String traceId,
        String fixtureId,
        String operatorId,
        String marketJson,
        Long stakeCents,
        String odds,
        Long payoutCents,
        Long maxAcceptableStakeCents,
        String reasonsJson,
        String versionsJson,
        String featureSnapshotJson,
        String evidenceJson,
        LocalDateTime createdAt
) {
    /** 兼容旧调用方的精简构造。 */
    public static RiskDecisionLog basic(
            Long id, String requestId, String orderId, String userId, String scenario,
            String strategyCode, String decision, int riskScore, String riskLevel,
            String hitRules, String reason, BigDecimal amount, String ip, String deviceId,
            Integer latencyMs, String source, LocalDateTime createdAt) {
        return new RiskDecisionLog(
                id, requestId, orderId, userId, scenario, strategyCode, decision, riskScore,
                riskLevel, hitRules, reason, amount, ip, deviceId, latencyMs, source,
                null, null, null, null, null, null, null, null, null, null, null, null, createdAt);
    }
}
