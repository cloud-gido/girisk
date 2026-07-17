package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.model.OrderPostStatus;
import com.girisk.flink.risk.model.OrderPostStatusUpdate;
import com.girisk.flink.risk.time.OrderEventTimes;

/**
 * 解析 {@code girisk.trading.order.risk-check.post.v1}：
 * CONFIRMED / REJECTED / CASHED_OUT。
 */
public final class OrderRiskPostJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OrderRiskPostJsonParser() {}

    public static OrderPostStatusUpdate parse(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            String eventType = text(root, "eventType");
            if (!"OrderRiskCheckEvent".equalsIgnoreCase(eventType)) {
                throw new IllegalArgumentException("post topic 期望 OrderRiskCheckEvent，当前=" + eventType);
            }
            JsonNode payload = require(root, "payload");
            OrderPostStatus status = OrderPostStatus.parse(text(payload, "status"));
            String orderId = resolveOrderId(root, payload);
            long eventTimeMs = resolveEventTimeMs(payload, status);
            String fixtureId = firstLegFixtureId(payload);

            FootballSportsOrder order = null;
            if (status == OrderPostStatus.CONFIRMED) {
                order = BetConfirmedEventJsonParser.parsePostConfirmed(json);
                if (fixtureId.isEmpty()) {
                    fixtureId = nz(order.fixtureId);
                }
            }
            return new OrderPostStatusUpdate(status, orderId, fixtureId, eventTimeMs, order);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("post 订单 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    private static long resolveEventTimeMs(JsonNode payload, OrderPostStatus status) {
        String confirmedAt = text(payload, "confirmedAt");
        if (status == OrderPostStatus.CONFIRMED && !confirmedAt.isEmpty()) {
            return parseTimeMillis(confirmedAt);
        }
        String betTime = text(payload, "betTime");
        if (!betTime.isEmpty()) {
            return parseTimeMillis(betTime);
        }
        return System.currentTimeMillis();
    }

    private static long parseTimeMillis(String iso) {
        try {
            return OrderEventTimes.parseOrderTimeMillis(iso);
        } catch (IllegalArgumentException e) {
            return java.time.Instant.parse(iso).toEpochMilli();
        }
    }

    private static String firstLegFixtureId(JsonNode payload) {
        JsonNode legs = payload.get("legs");
        if (legs == null || !legs.isArray() || legs.isEmpty()) {
            return "";
        }
        JsonNode leg = legs.get(0);
        JsonNode v = leg.get("fixtureId");
        if (v == null || v.isNull()) {
            return "";
        }
        return v.asText("").trim();
    }

    private static String resolveOrderId(JsonNode root, JsonNode payload) {
        String orderId = text(payload, "orderId");
        if (!orderId.isEmpty()) {
            return orderId;
        }
        orderId = text(root, "aggregateId");
        if (!orderId.isEmpty()) {
            return orderId;
        }
        throw new IllegalArgumentException("缺少 orderId");
    }

    private static JsonNode require(JsonNode parent, String field) {
        JsonNode n = parent.get(field);
        if (n == null || n.isNull()) {
            throw new IllegalArgumentException("缺少字段: " + field);
        }
        return n;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return "";
        }
        if (v.isNumber()) {
            return v.asText();
        }
        return v.asText("").trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
