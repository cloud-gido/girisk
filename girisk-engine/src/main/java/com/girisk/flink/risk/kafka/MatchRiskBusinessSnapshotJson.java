package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 业务 topic = summary 流 ∪ limit 流（union all）。
 *
 * <ul>
 *   <li>来一条 summary → {@code { summaryData: {...}, limitData: null }}
 *   <li>来一条 limit → {@code { summaryData: null, limitData: {...} }}
 * </ul>
 */
public final class MatchRiskBusinessSnapshotJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MatchRiskBusinessSnapshotJson() {}

    /** summary topic 同行 → business（去掉 {@code assumedScores}，limit 侧为空）。 */
    public static String fromSummary(String summaryJson) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            JsonNode summary = MAPPER.readTree(summaryJson);
            if (summary instanceof ObjectNode) {
                ((ObjectNode) summary).remove("assumedScores");
            }
            root.set("summaryData", summary);
            root.putNull("limitData");
            return root.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("包装 Summary → business 失败: " + e.getMessage(), e);
        }
    }

    /** limit topic 同行 → business（summary 侧为空）。 */
    public static String fromLimit(String limitJson) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.putNull("summaryData");
            root.set("limitData", MAPPER.readTree(limitJson));
            return root.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("包装 Limit → business 失败: " + e.getMessage(), e);
        }
    }
}
