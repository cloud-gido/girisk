package com.girisk.strategy.model;

import java.time.LocalDateTime;

public record RiskStrategy(
        Long id,
        String code,
        String name,
        String scenario,
        String description,
        boolean enabled,
        int priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
