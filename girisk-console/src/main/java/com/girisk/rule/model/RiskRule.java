package com.girisk.rule.model;

public record RiskRule(
        Long id,
        Long strategyId,
        String code,
        String name,
        String ruleType,
        String field,
        String operator,
        String threshold,
        String action,
        int scoreWeight,
        int priority,
        boolean enabled,
        String description,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt
) {
    public String listKeyOrField() {
        return field != null ? field : "userId";
    }
}
