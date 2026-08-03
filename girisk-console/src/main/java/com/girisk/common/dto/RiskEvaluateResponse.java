package com.girisk.common.dto;

import com.girisk.common.enums.RiskDecision;
import com.girisk.common.enums.RiskLevel;

import java.util.List;

public record RiskEvaluateResponse(
        String requestId,
        String orderId,
        RiskDecision decision,
        int riskScore,
        RiskLevel riskLevel,
        List<String> hitRules,
        String reason,
        String strategyCode,
        long latencyMs,
        String caseNo
) {}
