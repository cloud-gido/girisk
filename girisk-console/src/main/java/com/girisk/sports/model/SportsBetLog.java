package com.girisk.sports.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SportsBetLog(
        Long id,
        String requestId,
        String orderId,
        String matchCode,
        String marketType,
        String lineValue,
        String selection,
        BigDecimal amount,
        BigDecimal odds,
        String decision,
        BigDecimal maxAccept,
        boolean limitMode,
        String reason,
        Integer latencyMs,
        LocalDateTime createdAt
) {}
