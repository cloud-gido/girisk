package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.MatchExposureAggregator.ExposureSummary;
import com.girisk.flink.risk.grid.MatchExposureAggregator.ScenarioExposure;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.grid.ScoreGridSpec.ScoreScenario;
import com.girisk.flink.risk.model.EnrichedFootballOrder;

import java.util.ArrayList;
import java.util.List;

/** 场次窗口假设比分汇总 JSON：v7 为嵌套快照（每场一条），v6 行模型保留供兼容。 */
public final class MatchExposureSummaryJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MatchExposureSummaryJson() {}

    /**
     * schemaVersion=8：每场每次触发一条，内含全网格 {@code assumedScores} 与 {@code triggerOrder}。
     */
    public static String summarySnapshotJson(
            EnrichedFootballOrder trigger,
            String matchKey,
            boolean duplicateIgnored,
            boolean triggerRejected,
            boolean eventTimeOutOfOrder,
            int windowOrderCount,
            ExposureSummary exposure,
            ScoreGridParams gridParams,
            long publishedAtMs) {
        return summarySnapshotJson(
                trigger,
                matchKey,
                duplicateIgnored,
                triggerRejected,
                eventTimeOutOfOrder,
                windowOrderCount,
                exposure,
                null,
                gridParams,
                publishedAtMs);
    }

    /**
     * @param noRiskExposure 若提供：按「本场全部已见订单（含被拒）」汇总的对照敞口，写入
     *     {@code noRiskWorstPnlYuan}/{@code noRiskWorstScore}
     */
    public static String summarySnapshotJson(
            EnrichedFootballOrder trigger,
            String matchKey,
            boolean duplicateIgnored,
            boolean triggerRejected,
            boolean eventTimeOutOfOrder,
            int windowOrderCount,
            ExposureSummary exposure,
            ExposureSummary noRiskExposure,
            ScoreGridParams gridParams,
            long publishedAtMs) {
        MaxProfitMeta maxProfit = maxProfitMeta(exposure.scenarios);
        ObjectNode n = MAPPER.createObjectNode();
        n.put("schemaVersion", 8);
        n.put("eventId", RiskKafkaMessageIds.newEventId());
        n.put("upstreamEventId", RiskKafkaMessageIds.upstreamEventId(trigger.order));
        n.put("operatorId", trigger.order.operatorId);
        n.put("fixtureId", nz(trigger.order.fixtureId));
        n.put("matchKey", matchKey);
        n.put("league", nz(trigger.order.league));
        n.put("homeTeam", nz(trigger.order.homeTeam));
        n.put("awayTeam", nz(trigger.order.awayTeam));
        n.put("kickoffTime", nz(trigger.order.kickoffTime));
        n.put("triggerOrderId", nz(trigger.order.orderId));
        n.set("triggerOrder", TriggerOrderJson.nested(trigger));
        n.put("eventTimeMs", trigger.orderTimeMs);
        n.put("windowOrderCount", windowOrderCount);
        n.put("duplicateIgnored", duplicateIgnored);
        n.put("triggerRejected", triggerRejected);
        n.put("eventTimeOutOfOrder", eventTimeOutOfOrder);
        n.put("publishedAtMs", publishedAtMs);

        ObjectNode grid = MAPPER.createObjectNode();
        grid.put("baseHome", gridParams.baseHome);
        grid.put("baseAway", gridParams.baseAway);
        grid.put("gridSize", gridParams.grid.homeSpan());
        grid.put("homeSpan", gridParams.grid.homeSpan());
        grid.put("awaySpan", gridParams.grid.awaySpan());
        grid.put("homeMin", gridParams.grid.homeMin);
        grid.put("homeMax", gridParams.grid.homeMax);
        grid.put("awayMin", gridParams.grid.awayMin);
        grid.put("awayMax", gridParams.grid.awayMax);
        n.set("grid", grid);

        long windowStakeCents = 0L;
        if (!exposure.scenarios.isEmpty()) {
            windowStakeCents = exposure.scenarios.get(0).stakeSumCents;
        }
        n.put("windowStakeCents", windowStakeCents);
        n.put("windowStakeYuan", windowStakeCents / 100.0);

        if (maxProfit != null) {
            n.put("maxProfitCents", maxProfit.maxProfitCents);
            n.put("maxProfitYuan", maxProfit.maxProfitCents / 100.0);
            ArrayNode scores = MAPPER.createArrayNode();
            maxProfit.tiedScores.forEach(scores::add);
            n.set("maxProfitScores", scores);
        }

        if (noRiskExposure != null) {
            ScenarioExposure noRiskWorst = MatchExposureAggregator.worstScenario(noRiskExposure);
            if (noRiskWorst != null) {
                n.put("noRiskWorstPnlYuan", noRiskWorst.bookmakerPnlCents / 100.0);
                n.put("noRiskWorstScore", noRiskWorst.scenario.scoreLabel());
            }
        }

        ArrayNode assumed = MAPPER.createArrayNode();
        for (ScenarioExposure row : exposure.scenarios) {
            assumed.add(assumedScoreNode(row));
        }
        n.set("assumedScores", assumed);
        return n.toString();
    }

    private static ObjectNode assumedScoreNode(ScenarioExposure row) {
        long stake = row.stakeSumCents;
        long payable = row.platformPayableSumCents;
        long profit = row.profitCents();
        ObjectNode cell = MAPPER.createObjectNode();
        putAssumedScore(cell, row.scenario);
        cell.put("stakeCents", stake);
        cell.put("payableCents", payable);
        cell.put("profitCents", profit);
        cell.put("stakeYuan", stake / 100.0);
        cell.put("payableYuan", payable / 100.0);
        cell.put("profitYuan", profit / 100.0);
        return cell;
    }

    /** schemaVersion=6：每假设比分一行（历史格式，新作业不再写入 summary topic）。 */
    public static String summaryRowJson(
            EnrichedFootballOrder trigger,
            String matchKey,
            boolean duplicateIgnored,
            boolean eventTimeOutOfOrder,
            int windowOrderCount,
            ScenarioExposure row,
            MaxProfitMeta maxProfit) {
        long stake = row.stakeSumCents;
        long payable = row.platformPayableSumCents;
        long profit = row.profitCents();
        ObjectNode n = MAPPER.createObjectNode();
        n.put("schemaVersion", 6);
        n.put("fixtureId", nz(trigger.order.fixtureId));
        n.put("matchKey", matchKey);
        n.put("triggerOrderId", nz(trigger.order.orderId));
        n.put("eventTimeMs", trigger.orderTimeMs);
        n.put("windowOrderCount", windowOrderCount);
        n.put("duplicateIgnored", duplicateIgnored);
        n.put("eventTimeOutOfOrder", eventTimeOutOfOrder);
        putAssumedScore(n, row.scenario);
        n.put("stakeCents", stake);
        n.put("payableCents", payable);
        n.put("profitCents", profit);
        n.put("orderCount", windowOrderCount);
        n.put("stakeYuan", stake / 100.0);
        n.put("payableYuan", payable / 100.0);
        n.put("profitYuan", profit / 100.0);
        if (maxProfit != null) {
            n.put("maxProfitYuan", maxProfit.maxProfitCents / 100.0);
            ArrayNode scores = MAPPER.createArrayNode();
            maxProfit.tiedScores.forEach(scores::add);
            n.set("maxProfitScores", scores);
            n.put("maxProfitScore", String.join(",", maxProfit.tiedScores));
        }
        return n.toString();
    }

    private static void putAssumedScore(ObjectNode n, ScoreScenario scenario) {
        n.put("assumedScore", scenario.scoreLabel());
        n.put("homeScore", scenario.homeGoals);
        n.put("awayScore", scenario.awayGoals);
    }

    public static MaxProfitMeta maxProfitMeta(List<ScenarioExposure> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) {
            return null;
        }
        long maxProfitCents = Long.MIN_VALUE;
        for (ScenarioExposure row : scenarios) {
            maxProfitCents = Math.max(maxProfitCents, row.profitCents());
        }
        List<String> tiedScores = new ArrayList<>();
        for (ScenarioExposure row : scenarios) {
            if (row.profitCents() == maxProfitCents) {
                tiedScores.add(row.scenario.scoreLabel());
            }
        }
        return new MaxProfitMeta(maxProfitCents, tiedScores);
    }

    public static final class MaxProfitMeta {
        public final long maxProfitCents;
        public final List<String> tiedScores;

        public MaxProfitMeta(long maxProfitCents, List<String> tiedScores) {
            this.maxProfitCents = maxProfitCents;
            this.tiedScores = tiedScores;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
