package com.girisk.configcenter.model;

import java.time.LocalDateTime;

public record RiskConfigRelease(
        Long id,
        long configEpoch,
        String scope,
        String status,
        String paramSetVersion,
        String ruleSetVersion,
        String paramSetJson,
        String ruleSetJson,
        String changeSummary,
        String createdBy,
        String submittedBy,
        String approvedBy,
        String publishedBy,
        String approvalTicket,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime submittedAt,
        LocalDateTime approvedAt,
        LocalDateTime publishedAt
) {}
