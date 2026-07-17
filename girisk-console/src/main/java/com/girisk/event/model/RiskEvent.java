package com.girisk.event.model;

import java.time.LocalDateTime;

public record RiskEvent(
        Long id,
        String eventType,
        String severity,
        String orderId,
        String userId,
        String title,
        String detail,
        LocalDateTime createdAt
) {}
