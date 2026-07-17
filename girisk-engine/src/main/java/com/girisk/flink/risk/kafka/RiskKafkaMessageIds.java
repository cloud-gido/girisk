package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.excel.FootballSportsOrder;

import java.util.UUID;

/** 下游 Kafka JSON 幂等键 {@code eventId}（UUID）与上游关联字段。 */
public final class RiskKafkaMessageIds {

    private RiskKafkaMessageIds() {}

    /** 每条下游消息唯一幂等键。 */
    public static String newEventId() {
        return UUID.randomUUID().toString();
    }

    /** BetConfirmedEvent 根 {@code eventId}；无则空串。 */
    public static String upstreamEventId(FootballSportsOrder order) {
        if (order == null || order.eventId == null || order.eventId.isBlank()) {
            return "";
        }
        return order.eventId.trim();
    }
}
