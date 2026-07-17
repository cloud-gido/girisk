package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchRiskBusinessSnapshotJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fromSummaryUnionRow() throws Exception {
        String summary = sampleSummary();
        String limit = sampleLimit("NONE");

        JsonNode root = MAPPER.readTree(MatchRiskBusinessSnapshotJson.fromSummary(summary));
        assertTrue(root.has("summaryData"));
        assertTrue(root.get("limitData").isNull());

        JsonNode summaryData = root.get("summaryData");
        assertEquals(8, summaryData.get("schemaVersion").asInt());
        assertFalse(summaryData.has("assumedScores"));
    }

    @Test
    void fromLimitUnionRow() throws Exception {
        String summary = sampleSummary();
        String limit = sampleLimit("LIMIT");

        JsonNode root = MAPPER.readTree(MatchRiskBusinessSnapshotJson.fromLimit(limit));
        assertTrue(root.get("summaryData").isNull());
        assertTrue(root.has("limitData"));

        JsonNode limitData = root.get("limitData");
        assertEquals(4, limitData.get("schemaVersion").asInt());
        assertEquals("payout", limitData.get("basis").asText());
        assertEquals("LIMIT", limitData.get("rejectReason").asText());
    }

    private static String sampleSummary() throws Exception {
        EnrichedFootballOrder trigger =
                new EnrichedFootballOrder(sampleOrder(), 1L, "13344919||||");
        ScoreGridParams grid =
                ScoreGridParams.fromMap(Map.of("score", "0:0", "grid", "2"));
        var exposure = MatchExposureAggregator.summarize(List.of(sampleOrder()), grid.grid);
        return MatchExposureSummaryJson.summarySnapshotJson(
                trigger,
                "13344919||||",
                false,
                false,
                false,
                1,
                exposure,
                grid,
                99L);
    }

    private static String sampleLimit(String rejectReason) throws Exception {
        EnrichedFootballOrder trigger =
                new EnrichedFootballOrder(sampleOrder(), 1L, "13344919||||");
        return MatchLimitSummaryJson.limitSnapshotJson(
                trigger,
                "13344919||||",
                1,
                false,
                0.2,
                2000.0,
                List.of(sampleOrder()),
                List.of(sampleOrder()),
                rejectReason,
                12500.0,
                1000.0,
                false,
                99L);
    }

    private static com.girisk.flink.risk.excel.FootballSportsOrder sampleOrder() {
        com.girisk.flink.risk.excel.FootballSportsOrder o = new com.girisk.flink.risk.excel.FootballSportsOrder();
        o.fixtureId = "13344919";
        o.orderId = "324882365974691841";
        o.eventId = "4a513ce9-b0cf-4bd1-aaeb-16b50ff8d808";
        o.operatorId = 1L;
        o.playType = "1X2";
        o.selection = "主胜";
        o.handicapText = "无";
        o.parlayType = "单关";
        o.odds = 1.09;
        o.stakeYuan = 500;
        o.orderTime = "2026-06-15T12:06:32.070703801Z";
        o.userId = "NQTEST13146949BRL";
        return o;
    }
}
