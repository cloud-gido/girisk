package com.girisk.case_.model;

import java.time.LocalDateTime;

public record RiskCase(
        Long id,
        String caseNo,
        Long decisionLogId,
        String orderId,
        String userId,
        String operatorId,
        String status,
        String priority,
        int riskScore,
        String riskLevel,
        String assignee,
        String reviewDecision,
        String reviewComment,
        LocalDateTime slaDeadline,
        String callbackStatus,
        String callbackPayload,
        LocalDateTime callbackAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime reviewedAt
) {}
