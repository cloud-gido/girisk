package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.common.decision.RiskDecisionCodes;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.limit.ExposureLimitGate;
import com.girisk.flink.risk.limit.MatchTriggerAcceptance;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskDecisionJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ScoreGridParams GRID =
            ScoreGridParams.fromMap(Map.of("score", "0:0", "grid", "2"));

    @Test
    void productAuditContainsGate1FieldsOnLimitReject() throws Exception {
        FootballSportsOrder prior =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O1,2026-05-18 10:08:00,U1,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,100");
        FootballSportsOrder trigger =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O10,2026-05-18 10:12:00,U2,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,500");
        EnrichedFootballOrder enriched = new EnrichedFootballOrder(trigger, 1L, "k");

        MatchTriggerAcceptance acceptance =
                MatchTriggerAcceptance.evaluate(
                        List.of(prior),
                        enriched,
                        false,
                        GRID.grid,
                        0.2,
                        0.0,
                        ExposureLimitGate.WORST_LOSS_DISABLED,
                        false);

        String json =
                RiskDecisionJson.fromAcceptance(
                        enriched, acceptance, 0.2, 0.0, 1000.0, 1_700_000_000_000L, "test");
        JsonNode n = MAPPER.readTree(json);

        assertEquals(RiskDecisionCodes.REJECT, n.get("decision").asText());
        assertTrue(n.get("evidence").get("limitRejected").asBoolean());
        assertEquals("MATCH_RESULT", n.get("market").get("playTypeResolved").asText());
        assertTrue(n.get("featureSnapshot").has("beforeAccept"));
        assertTrue(n.get("featureSnapshot").has("trialAfterAccept"));
        assertTrue(n.get("evidence").has("gate1TriggerSelection"));

        JsonNode p = n.get("productAudit");
        assertEquals("O10", p.get("订单ID").asText());
        assertEquals("曼城 vs 利物浦", p.get("比赛").asText());
        assertEquals("MATCH_RESULT", p.get("玩法识别").asText());
        assertTrue(p.get("是否触发限额拦截").asBoolean());
        assertFalse(p.get("是否进行风险判断").asBoolean());
        assertEquals("LIMIT", p.get("拦截类型").asText());
        assertEquals(RiskDecisionCodes.REJECT, p.get("最终判断结果").asText());
        assertTrue(p.get("判断前当前盘口已投注金额（含初始，投注额×赔率口径）").asDouble() > 0);
        assertTrue(p.has("当前盘口可投注金额"));
        assertTrue(p.has("限额公式"));
        assertTrue(p.get("Genius判断结果").isNull());
        assertEquals(185.0, p.get("判断前当前盘口已投注金额（含初始，投注额×赔率口径）").asDouble());
    }

    @Test
    void productAuditPassIncludesAfterActualExposure() throws Exception {
        FootballSportsOrder trigger =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O20,2026-05-18 10:12:00,U2,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,100");
        EnrichedFootballOrder enriched = new EnrichedFootballOrder(trigger, 1L, "k");

        MatchTriggerAcceptance acceptance =
                MatchTriggerAcceptance.evaluate(
                        List.of(),
                        enriched,
                        false,
                        GRID.grid,
                        0.2,
                        100000.0,
                        ExposureLimitGate.WORST_LOSS_DISABLED,
                        false);

        String json =
                RiskDecisionJson.fromAcceptance(
                        enriched, acceptance, 0.2, 100000.0, 1_000_000.0, 99L, "test");
        JsonNode n = MAPPER.readTree(json);
        JsonNode p = n.get("productAudit");

        assertEquals(RiskDecisionCodes.PASS, n.get("decision").asText());
        assertFalse(p.get("是否触发限额拦截").asBoolean());
        assertTrue(p.get("是否进行风险判断").asBoolean());
        assertEquals(0.0, p.get("接收前累计投注金额").asDouble(), 0.001);
        assertEquals(100.0, p.get("假设接收后累计投注金额").asDouble(), 0.001);
        // 接单后实际窗口含本笔
        assertEquals(100.0, p.get("实际接收后累计投注金额").asDouble(), 0.001);
        assertFalse(p.get("实际接收后最差比分").asText().isEmpty());
    }
}
