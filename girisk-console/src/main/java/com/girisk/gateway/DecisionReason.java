package com.girisk.gateway;

import java.util.Map;

public record DecisionReason(
        String ruleId,
        int ruleVersion,
        String stage,
        String action,
        String message,
        Map<String, Object> evidence
) {}
