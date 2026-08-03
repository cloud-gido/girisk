package com.girisk.list.model;

import java.time.LocalDateTime;

public record RiskListEntry(
        Long id,
        String listType,
        String listKey,
        String listValue,
        String reason,
        String source,
        LocalDateTime expiresAt,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
