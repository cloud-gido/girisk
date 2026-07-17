package com.girisk.flink.risk.kafka;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballOrderUnifiedParserTest {

    @Test
    void acceptsOrderRiskCheckEventPending() {
        String json =
                "{\"eventType\":\"OrderRiskCheckEvent\",\"aggregateId\":\"1\",\"payload\":{"
                        + "\"status\":\"PENDING\",\"phase\":\"PRE_CONFIRM\",\"stake\":50,\"betTime\":\"2026-06-12T06:11:22Z\","
                        + "\"betType\":\"SINGLE\",\"playerId\":\"p1\","
                        + "\"legs\":[{\"fixtureId\":\"13344525\",\"legPick\":{\"type\":\"1X2\",\"side\":\"HOME\"},\"price\":2.0}]"
                        + "}}";
        assertTrue(FootballOrderUnifiedParser.tryParseForRiskPipeline(json).isOk());
    }

    @Test
    void rejectsBetConfirmedEventForRiskPipeline() {
        String json =
                "{\"eventType\":\"BetConfirmedEvent\",\"aggregateId\":\"1\",\"payload\":{"
                        + "\"status\":\"CONFIRMED\",\"stake\":1,\"betTime\":\"2026-06-12T06:11:22Z\","
                        + "\"betType\":\"SINGLE\",\"playerId\":\"p1\","
                        + "\"legs\":[{\"fixtureId\":\"13344525\",\"legPick\":{\"type\":\"1X2\",\"side\":\"HOME\"},\"price\":2.0}]"
                        + "}}";
        FootballOrderUnifiedParser.ParseOutcome outcome =
                FootballOrderUnifiedParser.tryParseForRiskPipeline(json);
        assertFalse(outcome.isOk());
        assertTrue(outcome.skipReason.contains("eventType=BetConfirmedEvent"));
    }

    @Test
    void rejectsOrderRiskCheckEventWithWrongStatus() {
        String json =
                "{\"eventType\":\"OrderRiskCheckEvent\",\"aggregateId\":\"1\",\"payload\":{"
                        + "\"status\":\"CONFIRMED\",\"stake\":50,\"betTime\":\"2026-06-12T06:11:22Z\","
                        + "\"betType\":\"SINGLE\",\"playerId\":\"p1\","
                        + "\"legs\":[{\"fixtureId\":\"13344525\",\"legPick\":{\"type\":\"1X2\",\"side\":\"HOME\"},\"price\":2.0}]"
                        + "}}";
        assertFalse(FootballOrderUnifiedParser.tryParseForRiskPipeline(json).isOk());
    }

    @Test
    void rejectsCsvLineForRiskPipelineByDefault() {
        String csv =
                "13883500,O1,2026-05-18 10:00:00,U1,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,100";
        FootballOrderUnifiedParser.ParseOutcome outcome =
                FootballOrderUnifiedParser.tryParseForRiskPipeline(csv);
        assertFalse(outcome.isOk());
        assertTrue(outcome.skipReason.contains("非 JSON"));
    }

    @Test
    void acceptsCsvLineWhenFlagEnabled() {
        String csv =
                "FX1005,FB202605180001,2026-06-01 10:12:00,U1001,模拟英超13,星海联,山城竞技,2026-06-02 20:00:00,胜平负,单关,无,主胜,1.86,¥100.00";
        FootballOrderUnifiedParser.ParseOutcome outcome =
                FootballOrderUnifiedParser.tryParseForRiskPipeline(csv, true);
        assertTrue(outcome.isOk());
        assertEquals("FX1005", outcome.order.fixtureId);
        assertEquals("FB202605180001", outcome.order.orderId);
        assertEquals(100L, outcome.order.stakeYuan);
    }
}
