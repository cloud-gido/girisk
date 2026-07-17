package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.girisk.flink.risk.excel.BetResultLabel;
import com.girisk.flink.risk.excel.FootballBetSettlement;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.PerOrderScoreMatrix.ScenarioLine;
import com.girisk.flink.risk.grid.ScoreGridSpec.ScoreScenario;

/** 矩阵 / 风险结果 Kafka JSON（UTF-8 一行一条，字段为 camelCase 英文键）。 */
public final class FootballOrderKafkaOutcomeJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FootballOrderKafkaOutcomeJson() {}

    /**
     * 单笔订单 × 一格假设比分：订单 14 字段（含 fixtureId）+ assumedScore、result、platformPayable*。
     * platformPayable 为用户总到账（本金 + 净盈利），非仅盈利。
     */
    public static String orderMatrixRowJson(
            FootballSportsOrder order, ScenarioLine line, long publishedAtMs) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("schemaVersion", 3);
        n.put("eventId", RiskKafkaMessageIds.newEventId());
        n.put("upstreamEventId", RiskKafkaMessageIds.upstreamEventId(order));
        n.put("publishedAtMs", publishedAtMs);
        putOrderFields(n, order);
        putAssumedScore(n, line.scenario);
        putResult(n, line.result);
        long payable =
                FootballBetSettlement.userPayableCents(
                        order, line.scenario.homeGoals, line.scenario.awayGoals);
        n.put("platformPayableCents", payable);
        n.put("platformPayableYuan", payable / 100.0);
        return n.toString();
    }

    /** 与 Kafka CSV 对应的订单字段（camelCase）。 */
    private static void putOrderFields(ObjectNode n, FootballSportsOrder o) {
        TriggerOrderJson.putFlatOrderFields(n, o);
        n.put("league", nz(o.league));
        n.put("homeTeam", nz(o.homeTeam));
        n.put("awayTeam", nz(o.awayTeam));
        n.put("kickoffTime", nz(o.kickoffTime));
    }

    private static void putAssumedScore(ObjectNode n, ScoreScenario scenario) {
        n.put("assumedScore", scenario.scoreLabel());
        n.put("homeScore", scenario.homeGoals);
        n.put("awayScore", scenario.awayGoals);
    }

    private static void putResult(ObjectNode n, BetResultLabel label) {
        n.put("result", label.name());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
