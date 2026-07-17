package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchExposureSummaryJsonV7Test {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void summarySnapshotContainsAssumedScoresArray() throws Exception {
        FootballSportsOrder o = KafkaFootballOrderCsvParser.parse(
                "13883500,ORD1,2026-05-18 10:00:00,U1,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,100");
        o.operatorId = 7L;
        o.eventId = "evt-summary";
        ScoreGridParams params = ScoreGridParams.fromMap(java.util.Map.of("score", "0:0", "grid", "2"));
        var exposure =
                MatchExposureAggregator.summarize(List.of(o), params.grid);
        String json =
                MatchExposureSummaryJson.summarySnapshotJson(
                        new EnrichedFootballOrder(o, 1L, "k"),
                        "k",
                        false,
                        false,
                        false,
                        1,
                        exposure,
                        params,
                        99L);
        JsonNode n = MAPPER.readTree(json);
        assertEquals(8, n.get("schemaVersion").asInt());
        assertEquals(7L, n.get("operatorId").asLong());
        java.util.UUID.fromString(n.get("eventId").asText());
        assertEquals("evt-summary", n.get("upstreamEventId").asText());
        JsonNode trigger = n.get("triggerOrder");
        assertEquals("ORD1", trigger.get("orderId").asText());
        assertEquals("胜平负", trigger.get("playType").asText());
        assertEquals("MATCH_RESULT", trigger.get("playMarketFamily").asText());
        assertEquals("主胜", trigger.get("selection").asText());
        assertEquals(100L, trigger.get("stakeYuan").asLong());
        assertEquals(10000L, trigger.get("stakeCents").asLong());
        assertEquals(1.85, trigger.get("odds").asDouble(), 0.001);
        assertEquals(4, n.get("assumedScores").size());
        assertTrue(n.has("maxProfitScores"));
        assertEquals(10000, n.get("windowStakeCents").asLong());
        assertEquals(10000, n.get("assumedScores").get(0).get("stakeCents").asLong());
    }
}
