package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchLimitSummaryJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void limitSnapshotContainsTriggerOrderAndV4Fields() throws Exception {
        FootballSportsOrder o =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,ORD1,2026-05-18 10:00:00,U1,英超,曼城,利物浦,2026-05-16 22:00:00,大小球,单关,2.5,大,1.90,200");
        o.operatorId = 3L;
        o.eventId = "evt-limit";
        EnrichedFootballOrder trigger = new EnrichedFootballOrder(o, 42L, "k");

        String json =
                MatchLimitSummaryJson.limitSnapshotJson(
                        trigger,
                        "k",
                        1,
                        false,
                        0.2,
                        2000.0,
                        List.of(o),
                        List.of(o),
                        "NONE",
                        100.0,
                        1000.0,
                        false,
                        99L);
        JsonNode n = MAPPER.readTree(json);
        assertEquals(4, n.get("schemaVersion").asInt());
        assertEquals("payout", n.get("basis").asText());
        assertEquals(2000.0, n.get("initialSeedPayoutYuan").asDouble());
        assertEquals("NONE", n.get("rejectReason").asText());
        assertEquals(1000.0, n.get("worstLossThresholdYuan").asDouble());
        assertEquals("priorToTrigger", n.get("limitBasis").asText());
        assertTrue(n.has("triggerOrder"));
        assertTrue(n.has("marketGroupsIncludingTrigger"));
        assertTrue(n.has("triggerSelection"));
    }

    @Test
    void marketGroupsUsePayoutAndExcludeTrigger() throws Exception {
        FootballSportsOrder prior =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O1,2026-05-18 10:00:00,U1,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,100");
        FootballSportsOrder trigger =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O3,2026-05-18 10:12:00,U2,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,500");
        EnrichedFootballOrder enriched = new EnrichedFootballOrder(trigger, 1L, "k");

        String json =
                MatchLimitSummaryJson.limitSnapshotJson(
                        enriched,
                        "k",
                        2,
                        false,
                        0.2,
                        0.0,
                        List.of(prior),
                        List.of(prior, trigger),
                        "LIMIT",
                        323.1,
                        1000.0,
                        false,
                        99L);
        JsonNode n = MAPPER.readTree(json);

        JsonNode priorHome = n.get("marketGroups").get(0).get("outcomes").get(0);
        JsonNode includingHome =
                n.get("marketGroupsIncludingTrigger").get(0).get("outcomes").get(0);

        // 返彩口径：100×1.85=185，500×1.85=925
        assertEquals("home", priorHome.get("selection").asText());
        assertEquals(185.0, priorHome.get("stake").asDouble());
        assertEquals(1110.0, includingHome.get("stake").asDouble());
        assertEquals(185.0, n.get("marketGroups").get(0).get("groupTotalStake").asDouble());
        assertEquals(
                1110.0,
                n.get("marketGroupsIncludingTrigger").get(0).get("groupTotalStake").asDouble());

        JsonNode ts = n.get("triggerSelection");
        assertTrue(ts.get("resolved").asBoolean());
        assertEquals("home", ts.get("selection").asText());
        assertEquals(185.0, ts.get("stakeBefore").asDouble());
        assertEquals(500, ts.get("proposedStake").asLong());
        assertEquals(925.0, ts.get("proposedPayout").asDouble());
        assertTrue(ts.get("acceptMaxBefore").asDouble() >= 0);
        assertTrue(ts.get("shouldReject").asBoolean());
    }

    @Test
    void postFeedbackLimitBasisUsesConfirmedOnly() throws Exception {
        FootballSportsOrder confirmed =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O1,2026-05-18 10:00:00,U1,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,100");
        FootballSportsOrder pending =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O3,2026-05-18 10:12:00,U2,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,500");
        EnrichedFootballOrder enriched = new EnrichedFootballOrder(pending, 1L, "k");

        String json =
                MatchLimitSummaryJson.limitSnapshotJson(
                        enriched,
                        "k",
                        1,
                        false,
                        0.2,
                        0.0,
                        List.of(confirmed),
                        List.of(confirmed, pending),
                        "NONE",
                        25.0,
                        1000.0,
                        true,
                        99L);
        JsonNode n = MAPPER.readTree(json);
        assertEquals("postConfirmedPrior", n.get("limitBasis").asText());
        assertEquals(
                "girisk.trading.order.risk-check.post.v1",
                n.get("confirmedOrderSource").asText());
        assertEquals(185.0, n.get("marketGroups").get(0).get("groupTotalStake").asDouble());
        assertEquals(
                1110.0,
                n.get("marketGroupsIncludingTrigger").get(0).get("groupTotalStake").asDouble());
    }

    @Test
    void coldStartSeedGivesFirstOrderCapacity() throws Exception {
        FootballSportsOrder small =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O3,2026-05-18 10:12:00,U2,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,100");
        EnrichedFootballOrder enriched = new EnrichedFootballOrder(small, 1L, "k");

        String json =
                MatchLimitSummaryJson.limitSnapshotJson(
                        enriched,
                        "k",
                        0,
                        false,
                        0.2,
                        2000.0,
                        List.of(),
                        List.of(small),
                        "NONE",
                        0.0,
                        1000.0,
                        true,
                        99L);
        JsonNode n = MAPPER.readTree(json);

        // 窗口为空但 trigger 分组被种子建组：首单容量 = (0.4×6000−2000)/0.6 = 666.67
        JsonNode ts = n.get("triggerSelection");
        assertEquals(2000.0, ts.get("stakeBefore").asDouble());
        assertEquals(666.67, ts.get("acceptMaxBefore").asDouble());
        // 185 < 666.67 → 放行
        assertFalse(ts.get("shouldReject").asBoolean());
    }
}
