package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 交易 envelope 轻量探测（不做完整业务校验）。 */
public final class TradingEnvelopePeek {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final boolean json;
    public final boolean readable;
    public final String eventType;
    public final String status;
    public final String phase;

    private TradingEnvelopePeek(
            boolean json, boolean readable, String eventType, String status, String phase) {
        this.json = json;
        this.readable = readable;
        this.eventType = eventType == null ? "" : eventType;
        this.status = status == null ? "" : status;
        this.phase = phase == null ? "" : phase;
    }

    public static TradingEnvelopePeek fromRaw(String raw) {
        if (!BetConfirmedEventJsonParser.looksLikeJson(raw)) {
            return new TradingEnvelopePeek(false, false, "", "", "");
        }
        try {
            JsonNode root = MAPPER.readTree(raw);
            JsonNode payload = root.get("payload");
            return new TradingEnvelopePeek(
                    true,
                    true,
                    text(root, "eventType"),
                    payload == null || payload.isNull() ? "" : text(payload, "status"),
                    payload == null || payload.isNull() ? "" : text(payload, "phase"));
        } catch (Exception e) {
            return new TradingEnvelopePeek(true, false, "", "", "");
        }
    }

    public boolean isExpectedRiskCheckPending() {
        return "OrderRiskCheckEvent".equalsIgnoreCase(eventType)
                && "PENDING".equalsIgnoreCase(status);
    }

    public boolean isExpectedRiskCheckPost() {
        if (!"OrderRiskCheckEvent".equalsIgnoreCase(eventType)) {
            return false;
        }
        return "CONFIRMED".equalsIgnoreCase(status)
                || "REJECTED".equalsIgnoreCase(status)
                || "CASHED_OUT".equalsIgnoreCase(status);
    }

    public String describeMismatch() {
        if (!json) {
            return "非 JSON envelope";
        }
        if (!readable) {
            return "JSON 无法读取 eventType/status";
        }
        return String.format(
                "eventType=%s status=%s phase=%s",
                emptyAsDash(eventType), emptyAsDash(status), emptyAsDash(phase));
    }

    private static String emptyAsDash(String s) {
        return s.isEmpty() ? "-" : s;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return "";
        }
        return v.asText("").trim();
    }
}
