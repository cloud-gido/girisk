package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.flink.risk.grid.PerOrderScoreMatrix;
import com.girisk.flink.risk.grid.ScoreGridSpec;
import com.girisk.flink.risk.kafka.KafkaFootballOrderCsvParser;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballOrderKafkaOutcomeJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CJK_KEY = Pattern.compile("[\\u4e00-\\u9fff]");

    private static final String SAMPLE_CSV =
            "13883500,FB202605180001,2026-05-18 10:12:00,U1001,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,胜平负,单关,无,主胜,1.86,100";

    @Test
    void matrixRowJsonUsesCamelCaseEnglishKeysOnly() throws Exception {
        var order = KafkaFootballOrderCsvParser.parse(SAMPLE_CSV);
        order.eventId = "evt-upstream";
        var line =
                PerOrderScoreMatrix.expand(order, new ScoreGridSpec(2, 2, 1, 1)).get(0);
        String json = FootballOrderKafkaOutcomeJson.orderMatrixRowJson(order, line, 99L);
        JsonNode n = MAPPER.readTree(json);

        n.fieldNames().forEachRemaining(name -> assertFalse(CJK_KEY.matcher(name).find(), name));

        assertEquals(0, n.get("operatorId").asLong());
        java.util.UUID.fromString(n.get("eventId").asText());
        assertEquals("evt-upstream", n.get("upstreamEventId").asText());
        assertEquals(99L, n.get("publishedAtMs").asLong());
        assertEquals("13883500", n.get("fixtureId").asText());
        assertEquals("FB202605180001", n.get("orderId").asText());
        assertEquals("2:1", n.get("assumedScore").asText());
        assertEquals(2, n.get("homeScore").asInt());
        assertEquals(1, n.get("awayScore").asInt());
        assertEquals("WIN", n.get("result").asText());
        assertFalse(n.has("resultLabel"));
        assertEquals(18600, n.get("platformPayableCents").asLong());
        assertTrue(n.has("playMarketFamily"));
    }
}
