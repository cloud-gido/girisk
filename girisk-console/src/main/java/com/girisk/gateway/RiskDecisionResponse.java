package com.girisk.gateway;

import com.girisk.common.enums.RiskDecision;
import com.girisk.common.enums.RiskLevel;

import java.util.List;
import java.util.Map;

public record RiskDecisionResponse(
        String traceId,
        String requestId,
        String orderId,
        RiskDecision decision,
        int riskScore,
        RiskLevel riskLevel,
        List<DecisionReason> reasons,
        Map<String, Object> versions,
        Map<String, Object> featureSnapshot,
        Long maxAcceptableStakeCents,
        Long payoutCents,
        String fixtureId,
        String operatorId,
        String strategyCode,
        long latencyMs,
        String caseNo,
        String reason,
        // 体育限额诊断（可选，兼容旧前端）
        Boolean limitMode,
        Map<String, Object> sportsDetail
) {}
