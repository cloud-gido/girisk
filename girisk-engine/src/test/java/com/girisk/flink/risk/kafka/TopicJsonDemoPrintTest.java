package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.limit.ExposureLimitGate;
import com.girisk.flink.risk.limit.MatchTriggerAcceptance;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 打印 Kafka topic 示例 JSON（含 {@code girisk.decision.v1}）。
 *
 * <pre>
 * mvn -pl girisk-engine -am test -Dtest=TopicJsonDemoPrintTest \
 *   -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
class TopicJsonDemoPrintTest {

    private static final ObjectMapper PRETTY =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final ScoreGridParams GRID =
            ScoreGridParams.fromMap(Map.of("score", "0:0", "grid", "2"));

    @Test
    void printDecisionV1Demos() throws Exception {
        System.out.println("===== INPUT topic: girisk.trading.order.risk-check.v1 (CSV) =====");
        System.out.println(
                "13883500,O10,2026-05-18 10:12:00,U2,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,500");
        System.out.println();
        System.out.println(
                "===== INPUT topic: girisk.trading.order.risk-check.v1 (OrderRiskCheckEvent JSON) =====");
        System.out.println(sampleOrderRiskCheckJson("O10", "主胜", 1.85, 50000));
        System.out.println();

        // REJECT — Gate1 限额（无种子，主胜已占 185 返彩）
        FootballSportsOrder prior =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O1,2026-05-18 10:08:00,U1,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,100");
        FootballSportsOrder rejectTrigger =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O10,2026-05-18 10:12:00,U2,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,500");
        rejectTrigger.operatorId = 1001L;
        rejectTrigger.eventId = "tr-demo-reject-limit";
        EnrichedFootballOrder rejectEnriched =
                new EnrichedFootballOrder(rejectTrigger, 1779077520000L, "k");
        MatchTriggerAcceptance rejectAcc =
                MatchTriggerAcceptance.evaluate(
                        List.of(prior),
                        rejectEnriched,
                        false,
                        GRID.grid,
                        0.2,
                        0.0,
                        ExposureLimitGate.WORST_LOSS_DISABLED,
                        false);
        String rejectJson =
                RiskDecisionJson.fromAcceptance(
                        rejectEnriched, rejectAcc, 0.2, 0.0, 1000.0, 1779077520100L, "demo");

        System.out.println("===== OUTPUT topic: girisk.decision.v1 (REJECT / Gate1 LIMIT) =====");
        System.out.println(PRETTY.readTree(rejectJson).toPrettyString());
        System.out.println();

        // PASS — 大种子 + 关闭敞口阈
        FootballSportsOrder passTrigger =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O20,2026-05-18 10:12:00,U2,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,100");
        passTrigger.operatorId = 1001L;
        passTrigger.eventId = "tr-demo-pass";
        EnrichedFootballOrder passEnriched =
                new EnrichedFootballOrder(passTrigger, 1779077520000L, "k");
        MatchTriggerAcceptance passAcc =
                MatchTriggerAcceptance.evaluate(
                        List.of(),
                        passEnriched,
                        false,
                        GRID.grid,
                        0.2,
                        100000.0,
                        ExposureLimitGate.WORST_LOSS_DISABLED,
                        false);
        String passJson =
                RiskDecisionJson.fromAcceptance(
                        passEnriched, passAcc, 0.2, 100000.0, 1_000_000.0, 1779077520200L, "demo");

        System.out.println("===== OUTPUT topic: girisk.decision.v1 (PASS) =====");
        System.out.println(PRETTY.readTree(passJson).toPrettyString());
    }

    private static String sampleOrderRiskCheckJson(
            String orderId, String selection, double odds, long stakeCents) {
        return String.format(
                java.util.Locale.ROOT,
                "{"
                        + "\"envelopeVersion\":1,"
                        + "\"eventType\":\"OrderRiskCheckEvent\","
                        + "\"eventId\":\"tr-demo-%s\","
                        + "\"operatorId\":\"1001\","
                        + "\"payload\":{"
                        + "\"orderId\":\"%s\","
                        + "\"status\":\"PENDING\","
                        + "\"betType\":\"SINGLE\","
                        + "\"betTime\":\"2026-05-18T10:12:00Z\","
                        + "\"playerId\":\"U2\","
                        + "\"stakeCents\":%d,"
                        + "\"odds\":%s,"
                        + "\"legs\":[{"
                        + "\"fixtureId\":\"13883500\","
                        + "\"legPick\":{\"marketType\":\"胜平负\",\"selection\":\"%s\",\"line\":\"\"}"
                        + "}]"
                        + "}}",
                orderId,
                orderId,
                stakeCents,
                odds,
                selection);
    }
}
