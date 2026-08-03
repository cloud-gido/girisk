package com.girisk.engine;

import com.girisk.common.dto.RiskEvaluateRequest;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public final class RiskContext {

    private final Map<String, Object> fields = new HashMap<>();

    private RiskContext() {}

    public static RiskContext from(RiskEvaluateRequest req) {
        RiskContext ctx = new RiskContext();
        ctx.put("orderId", req.orderId());
        ctx.put("userId", req.userId());
        ctx.put("amount", req.amount());
        ctx.put("currency", defaultStr(req.currency(), "CNY"));
        ctx.put("paymentMethod", defaultStr(req.paymentMethod(), "UNKNOWN"));
        ctx.put("ip", defaultStr(req.ip(), ""));
        ctx.put("deviceId", defaultStr(req.deviceId(), ""));
        ctx.put("merchantId", defaultStr(req.merchantId(), ""));
        ctx.put("productCategory", defaultStr(req.productCategory(), "GENERAL"));
        ctx.put("country", defaultStr(req.country(), "CN"));
        ctx.put("orderCount24h", req.orderCount24h() != null ? req.orderCount24h() : 0);
        ctx.put("amountSum24h", req.amountSum24h() != null ? req.amountSum24h() : BigDecimal.ZERO);
        ctx.put("isNewUser", req.isNewUser() != null ? req.isNewUser() : false);
        ctx.put("deviceRiskScore", req.deviceRiskScore() != null ? req.deviceRiskScore() : 0);
        return ctx;
    }

    public Object get(String key) {
        return fields.get(key);
    }

    public void put(String key, Object value) {
        fields.put(key, value);
    }

    private static String defaultStr(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
