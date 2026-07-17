package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.limit.ExposureLimitGate;
import com.girisk.flink.risk.limit.MatchTriggerAcceptance;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import com.girisk.flink.risk.model.MatchKeys;
import com.girisk.flink.risk.time.OrderEventTimes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 模拟两笔 OrderRiskCheckEvent 顺序到达后的 Summary / Limit 输出。 */
class OrderRiskCheckTwoOrdersSimulationTest {

    private static final ObjectMapper PRETTY =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final String ORDER_1 =
            "{"
                    + "\"envelopeVersion\":\"1\","
                    + "\"operatorId\":1,"
                    + "\"sourceType\":\"TRADING\","
                    + "\"eventId\":\"8390a4e8-6b36-41a0-b625-6aa7a513a7fe\","
                    + "\"eventType\":\"OrderRiskCheckEvent\","
                    + "\"aggregateType\":\"ORDER\","
                    + "\"aggregateId\":\"324817508474667009\","
                    + "\"payload\":{"
                    + "\"orderId\":\"324817508474667009\","
                    + "\"betType\":\"SINGLE\","
                    + "\"status\":\"PENDING\","
                    + "\"phase\":\"PRE_CONFIRM\","
                    + "\"stake\":11.0,"
                    + "\"playerId\":\"NQTEST13146949BRL\","
                    + "\"betTime\":\"2026-06-15T07:48:48.842612757Z\","
                    + "\"legs\":[{\"fixtureId\":\"13344919\","
                    + "\"legPick\":{\"type\":\"1X2\",\"line\":0.0,\"side\":\"HOME\"},\"price\":1.09}]"
                    + "}}";

    private static final String ORDER_2 =
            "{"
                    + "\"envelopeVersion\":\"1\","
                    + "\"operatorId\":1,"
                    + "\"sourceType\":\"TRADING\","
                    + "\"eventId\":\"80a72473-e467-482b-bc60-f178895a0611\","
                    + "\"eventType\":\"OrderRiskCheckEvent\","
                    + "\"aggregateType\":\"ORDER\","
                    + "\"aggregateId\":\"324832347922219009\","
                    + "\"payload\":{"
                    + "\"orderId\":\"324832347922219009\","
                    + "\"betType\":\"SINGLE\","
                    + "\"status\":\"PENDING\","
                    + "\"phase\":\"PRE_CONFIRM\","
                    + "\"stake\":100.0,"
                    + "\"playerId\":\"NQTEST13146949BRL\","
                    + "\"betTime\":\"2026-06-15T08:47:46.837645853Z\","
                    + "\"legs\":[{\"fixtureId\":\"13344919\","
                    + "\"legPick\":{\"type\":\"1X2\",\"line\":0.0,\"side\":\"HOME\"},\"price\":1.09}]"
                    + "}}";

    @Test
    void simulateTwoOrdersSequential() throws Exception {
        ScoreGridParams grid = ScoreGridParams.fromMap(Map.of("score", "0:0", "grid", "6"));
        double limitDelta = 0.2;
        double seedPayoutYuan = 2000.0;
        double maxWorstLossYuan = 1000.0;

        List<FootballSportsOrder> accepted = new ArrayList<>();
        processOrder("第 1 笔", ORDER_1, accepted, grid, limitDelta, seedPayoutYuan, maxWorstLossYuan);
        processOrder("第 2 笔", ORDER_2, accepted, grid, limitDelta, seedPayoutYuan, maxWorstLossYuan);

        System.out.printf(Locale.ROOT, "%n===== 最终窗口已接单 %d 笔 =====%n", accepted.size());
        for (FootballSportsOrder o : accepted) {
            System.out.printf(
                    Locale.ROOT,
                    "  orderId=%s stakeYuan=%d selection=%s odds=%.2f%n",
                    o.orderId,
                    o.stakeYuan,
                    o.selection,
                    o.odds);
        }
    }

    private static void processOrder(
            String label,
            String json,
            List<FootballSportsOrder> acceptedState,
            ScoreGridParams grid,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan)
            throws Exception {
        FootballOrderUnifiedParser.ParseOutcome parsed =
                FootballOrderUnifiedParser.tryParseForRiskPipeline(json);
        if (!parsed.isOk()) {
            throw new IllegalStateException("解析失败: " + parsed.skipReason);
        }
        FootballSportsOrder order = parsed.order;
        long orderTimeMs = OrderEventTimes.parseOrderTimeMillis(order.orderTime);
        EnrichedFootballOrder trigger = new EnrichedFootballOrder(order, orderTimeMs, MatchKeys.of(order));

        MatchTriggerAcceptance acceptance =
                MatchTriggerAcceptance.evaluate(
                        List.copyOf(acceptedState),
                        trigger,
                        false,
                        grid.grid,
                        limitDelta,
                        seedPayoutYuan,
                        maxWorstLossYuan,
                        false);

        if (acceptance.persistTrigger()) {
            acceptedState.add(order);
        }

        List<String> summaries = new ArrayList<>();
        List<String> limits = new ArrayList<>();
        var summaryExposure = MatchExposureAggregator.summarize(acceptance.acceptedOrders, grid.grid);
        summaries.add(
                MatchExposureSummaryJson.summarySnapshotJson(
                        trigger,
                        MatchKeys.of(order),
                        acceptance.duplicateIgnored,
                        acceptance.triggerRejected,
                        false,
                        acceptance.acceptedOrders.size(),
                        summaryExposure,
                        grid,
                        System.currentTimeMillis()));
        limits.add(
                MatchLimitSummaryJson.limitSnapshotJson(
                        trigger,
                        MatchKeys.of(order),
                        acceptance.acceptedOrders.size(),
                        acceptance.duplicateIgnored,
                        limitDelta,
                        seedPayoutYuan,
                        acceptance.confirmedOrders,
                        acceptance.trialOrdersIncludingTrigger,
                        acceptance.rejectReason.name(),
                        ExposureLimitGate.maxExposureYuan(acceptance.trialExposure),
                        maxWorstLossYuan,
                        acceptance.postFeedbackMode,
                        System.currentTimeMillis()));

        System.out.println();
        System.out.println("========== " + label + " orderId=" + order.orderId + " ==========");
        System.out.printf(
                Locale.ROOT,
                "决策: rejectReason=%s shouldReject=%s triggerRejected=%s windowOrderCount=%d%n",
                acceptance.rejectReason,
                acceptance.shouldReject,
                acceptance.triggerRejected,
                acceptance.acceptedOrders.size());
        System.out.println("----- SUMMARY -----");
        System.out.println(pretty(summaries.get(0)));
        System.out.println("----- LIMIT -----");
        System.out.println(pretty(limits.get(0)));
    }

    private static String pretty(String json) throws Exception {
        JsonNode node = PRETTY.readTree(json);
        return PRETTY.writeValueAsString(node);
    }
}
