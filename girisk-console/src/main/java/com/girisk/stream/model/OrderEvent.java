package com.girisk.stream.model;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderEvent(
        String eventId,
        String orderId,
        String userId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String ip,
        String deviceId,
        String merchantId,
        String productCategory,
        String country,
        Integer orderCount24h,
        BigDecimal amountSum24h,
        Boolean isNewUser,
        Integer deviceRiskScore,
        String scenario,
        Instant eventTime
) {}
