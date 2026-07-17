package com.girisk.flink.risk.model;

/** {@code girisk.trading.order.risk-check.post.v1} 回传状态。 */
public enum OrderPostStatus {
    CONFIRMED,
    REJECTED,
    CASHED_OUT;

    public static OrderPostStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("缺少 payload.status");
        }
        return OrderPostStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
