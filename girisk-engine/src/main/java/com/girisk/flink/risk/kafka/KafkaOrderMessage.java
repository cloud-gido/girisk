package com.girisk.flink.risk.kafka;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/** 写入 Kafka 的订单消息（key=订单号，value=CSV 行）。 */
public final class KafkaOrderMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public String orderId;
    public String payload;

    public KafkaOrderMessage() {}

    public KafkaOrderMessage(String orderId, String payload) {
        this.orderId = orderId;
        this.payload = payload;
    }

    public byte[] keyBytes() {
        return orderId.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] valueBytes() {
        return payload.getBytes(StandardCharsets.UTF_8);
    }
}
