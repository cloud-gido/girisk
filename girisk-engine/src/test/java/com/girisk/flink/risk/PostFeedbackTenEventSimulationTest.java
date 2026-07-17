package com.girisk.flink.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.kafka.FootballOrderUnifiedParser;
import com.girisk.flink.risk.kafka.MatchLimitSummaryJson;
import com.girisk.flink.risk.limit.ExposureLimitGate;
import com.girisk.flink.risk.limit.MatchTriggerAcceptance;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import com.girisk.flink.risk.model.MatchKeys;
import com.girisk.flink.risk.model.OrderPostStatusUpdate;
import com.girisk.flink.risk.time.OrderEventTimes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模拟 10 条 pre/post 事件（交易 envelope 格式），验证 post 回传模式下 state / summary / limit 逻辑。
 *
 * <p>运行并打印明细：{@code mvn test -Dtest=PostFeedbackTenEventSimulationTest}
 */
class PostFeedbackTenEventSimulationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FIXTURE = "14057476";
    private static final double LIMIT_DELTA = 0.2;
    /** 冷启动虚拟种子（返彩口径，产品计算器默认）。 */
    private static final double SEED_PAYOUT = 2000.0;
    /** Gate 2 最差净亏阈值（产品示例）。 */
    private static final double MAX_WORST_LOSS = 1000.0;

    @Test
    void tenEventPostFeedbackPipeline() throws Exception {
        ScoreGridParams grid = ScoreGridParams.fromMap(Map.of("score", "0:0", "grid", "6"));
        List<MatchExposureKafkaProcessFunction.StoredOrder> state = new ArrayList<>();
        java.util.Map<String, String> orderFixtureIndex = new java.util.HashMap<>();

        List<SimEvent> events =
                List.of(
                        pre("1", "328510871913336833", 100, "1X2", "HOME", 0, 1.38),
                        postConfirmed("2", "328510871913336833", 100, "1X2", "HOME", 0, 1.38),
                        pre("3", "328510871913336834", 50, "1X2", "HOME", 0, 1.38),
                        postConfirmed("4", "328510871913336834", 50, "1X2", "HOME", 0, 1.38),
                        pre("5", "328510871913336835", 800, "1X2", "HOME", 0, 1.38),
                        postRejected("6", "328510871913336835"),
                        pre("7", "328506241570066433", 30, "OU", "OVER", 3.0, 2.15),
                        postConfirmed("8", "328506241570066433", 30, "OU", "OVER", 3.0, 2.15),
                        postCashedOut("9", "328510871913336833"),
                        pre("10", "328510871913336836", 20, "1X2", "HOME", 0, 1.38));

        System.out.println("===== 10 条 pre/post 模拟（fixtureId=" + FIXTURE + "）=====");

        for (SimEvent event : events) {
            if (event.kind == Kind.PRE) {
                runPre(event, state, orderFixtureIndex, grid);
            } else {
                runPost(event, state, orderFixtureIndex);
            }
        }

        assertEquals(2, state.size(), "最终 state 应为 O002 + O004（O001 跳车，O003 拒单未入窗）");
        assertConfirmedIds(state, "328510871913336834", "328506241570066433");
    }

    private static void runPre(
            SimEvent event,
            List<MatchExposureKafkaProcessFunction.StoredOrder> state,
            java.util.Map<String, String> orderFixtureIndex,
            ScoreGridParams grid)
            throws Exception {
        FootballOrderUnifiedParser.ParseOutcome parsed =
                FootballOrderUnifiedParser.tryParseForRiskPipeline(event.json);
        assertTrue(parsed.isOk(), "pre 解析失败: " + event.label);
        FootballSportsOrder order = parsed.order;
        orderFixtureIndex.put(order.orderId, order.fixtureId);

        long orderTimeMs = OrderEventTimes.parseOrderTimeMillis(order.orderTime);
        EnrichedFootballOrder trigger = new EnrichedFootballOrder(order, orderTimeMs, MatchKeys.of(order));

        List<FootballSportsOrder> confirmed = ConfirmedOrderWindowState.toOrders(state);
        boolean duplicate =
                ConfirmedOrderWindowState.containsOrderId(
                        state, ConfirmedOrderWindowState.normalizeOrderId(order.orderId));

        MatchTriggerAcceptance acceptance =
                MatchTriggerAcceptance.evaluate(
                        confirmed,
                        trigger,
                        duplicate,
                        grid.grid,
                        LIMIT_DELTA,
                        SEED_PAYOUT,
                        MAX_WORST_LOSS,
                        true);

        String limitJson =
                MatchLimitSummaryJson.limitSnapshotJson(
                        trigger,
                        MatchKeys.of(order),
                        acceptance.acceptedOrders.size(),
                        acceptance.duplicateIgnored,
                        LIMIT_DELTA,
                        SEED_PAYOUT,
                        acceptance.confirmedOrders,
                        acceptance.trialOrdersIncludingTrigger,
                        acceptance.rejectReason.name(),
                        ExposureLimitGate.maxExposureYuan(acceptance.trialExposure),
                        MAX_WORST_LOSS,
                        true,
                        System.currentTimeMillis());

        JsonNode limit = MAPPER.readTree(limitJson);
        // 返彩口径 + 每盘口种子 2000（1X2 三向 = 基底 6000，OU 两向 = 基底 4000）
        double oneXTwoPriorPayout = groupPayout(limit.get("marketGroups"), "ONE_X_TWO");
        double oneXTwoIncludingPayout =
                groupPayout(limit.get("marketGroupsIncludingTrigger"), "ONE_X_TWO");

        System.out.printf(
                Locale.ROOT,
                "%n[%s] PRE PENDING orderId=%s stake=%d play=%s%n"
                        + "  state(CONFIRMED)=%d windowOrderCount=%d limitBasis=%s%n"
                        + "  1X2.priorPayout=%.2f 1X2.includingTrigger=%.2f rejectReason=%s shouldReject=%s%n",
                event.label,
                order.orderId,
                order.stakeYuan,
                order.playType,
                state.size(),
                acceptance.acceptedOrders.size(),
                limit.get("limitBasis").asText(),
                oneXTwoPriorPayout,
                oneXTwoIncludingPayout,
                acceptance.rejectReason,
                acceptance.shouldReject);

        assertEquals("postConfirmedPrior", limit.get("limitBasis").asText());
        assertEquals(state.size(), acceptance.acceptedOrders.size(), event.label + " summary 窗口应=state");

        switch (event.label) {
            case "1":
                assertEquals(0, state.size());
                assertEquals(0, acceptance.acceptedOrders.size());
                // 冷启动：种子建组 3×2000；试探 +100×1.38=138
                assertEquals(6000.0, oneXTwoPriorPayout, 0.01);
                assertEquals(6138.0, oneXTwoIncludingPayout, 0.01);
                assertEquals(MatchTriggerAcceptance.RejectReason.NONE, acceptance.rejectReason);
                break;
            case "3":
                assertEquals(1, state.size());
                assertEquals(1, acceptance.acceptedOrders.size());
                assertEquals(6138.0, oneXTwoPriorPayout, 0.01, "已 CONFIRMED 138 + 种子 6000");
                assertEquals(6207.0, oneXTwoIncludingPayout, 0.01, "试探 +50×1.38=69");
                assertEquals(MatchTriggerAcceptance.RejectReason.NONE, acceptance.rejectReason);
                break;
            case "5":
                assertEquals(2, state.size());
                assertEquals(6207.0, oneXTwoPriorPayout, 0.01, "limit 基数=两笔 CONFIRMED 207 + 种子");
                assertEquals(7311.0, oneXTwoIncludingPayout, 0.01, "试探 +800×1.38=1104");
                // b_max 主胜 = (0.4×6207−2207)/0.6 = 459.67 < 1104 → Gate 1 拒
                assertEquals(MatchTriggerAcceptance.RejectReason.LIMIT, acceptance.rejectReason);
                assertTrue(acceptance.shouldReject);
                break;
            case "7":
                assertEquals(2, state.size());
                assertEquals(2, acceptance.acceptedOrders.size());
                assertEquals(6207.0, oneXTwoPriorPayout, 0.01, "1X2 组两笔 CONFIRMED + 种子");
                assertEquals(
                        4064.5,
                        groupPayout(limit.get("marketGroupsIncludingTrigger"), "OVER_UNDER"),
                        0.01,
                        "OU 组种子 4000 + 30×2.15=64.5");
                assertEquals(MatchTriggerAcceptance.RejectReason.NONE, acceptance.rejectReason);
                break;
            case "10":
                assertEquals(2, state.size(), "O001 跳车后剩 O002 + O004");
                assertEquals(2, acceptance.acceptedOrders.size(), "summary 窗口=全部 CONFIRMED");
                assertEquals(6069.0, oneXTwoPriorPayout, 0.01, "仅 O002=69 + 种子（O001 已跳车）");
                assertEquals(6096.6, oneXTwoIncludingPayout, 0.01, "试探 +20×1.38=27.6");
                assertEquals(MatchTriggerAcceptance.RejectReason.NONE, acceptance.rejectReason);
                break;
            default:
                break;
        }

        assertFalse(acceptance.postFeedbackMode && state.size() < acceptance.acceptedOrders.size());
    }

    private static double groupPayout(JsonNode groups, String marketType) {
        if (groups == null || !groups.isArray()) {
            return 0;
        }
        for (JsonNode group : groups) {
            if (marketType.equals(group.path("marketType").asText())) {
                return group.path("groupTotalStake").asDouble();
            }
        }
        return 0;
    }

    private static void runPost(
            SimEvent event,
            List<MatchExposureKafkaProcessFunction.StoredOrder> state,
            java.util.Map<String, String> orderFixtureIndex) {
        FootballOrderUnifiedParser.PostParseOutcome parsed =
                FootballOrderUnifiedParser.tryParsePostStatus(event.json);
        assertTrue(parsed.isOk(), "post 解析失败: " + event.label + " " + parsed.skipReason);
        OrderPostStatusUpdate update = parsed.update;
        if (update.fixtureId.isEmpty()) {
            update = update.withFixtureId(orderFixtureIndex.get(update.orderId));
        }
        assertFalse(update.fixtureId.isBlank(), event.label + " 缺少 fixtureId");
        ConfirmedOrderWindowState.applyPostUpdate(state, update);

        System.out.printf(
                Locale.ROOT,
                "%n[%s] POST %s orderId=%s → state(CONFIRMED)=%d（无 summary/limit 输出）%n",
                event.label,
                event.kind.name(),
                event.orderId,
                state.size());

        switch (event.label) {
            case "2":
                assertEquals(1, state.size());
                break;
            case "4":
                assertEquals(2, state.size());
                break;
            case "6":
                assertEquals(2, state.size(), "REJECTED 不入窗");
                break;
            case "8":
                assertEquals(3, state.size());
                break;
            case "9":
                assertEquals(2, state.size(), "CASHED_OUT 移除 O001");
                break;
            default:
                break;
        }
    }

    private static void assertConfirmedIds(
            List<MatchExposureKafkaProcessFunction.StoredOrder> state, String... orderIds) {
        List<String> ids = new ArrayList<>();
        for (MatchExposureKafkaProcessFunction.StoredOrder s : state) {
            ids.add(s.order.orderId);
        }
        assertEquals(orderIds.length, ids.size());
        for (String id : orderIds) {
            assertTrue(ids.contains(id), "缺少 orderId=" + id + " actual=" + ids);
        }
    }

    private static SimEvent pre(
            String label,
            String orderId,
            double stake,
            String market,
            String side,
            double line,
            double price) {
        return new SimEvent(Kind.PRE, label, orderId, pendingJson(orderId, stake, market, side, line, price));
    }

    private static SimEvent postConfirmed(
            String label,
            String orderId,
            double stake,
            String market,
            String side,
            double line,
            double price) {
        return new SimEvent(
                Kind.POST_CONFIRMED,
                label,
                orderId,
                confirmedJson(orderId, stake, market, side, line, price));
    }

    private static SimEvent postRejected(String label, String orderId) {
        return new SimEvent(Kind.POST_REJECTED, label, orderId, rejectedJson(orderId));
    }

    private static SimEvent postCashedOut(String label, String orderId) {
        return new SimEvent(Kind.POST_CASHED_OUT, label, orderId, cashedOutJson(orderId));
    }

    private static String pendingJson(
            String orderId, double stake, String market, String side, double line, double price) {
        return envelope(
                orderId,
                "PENDING",
                "PRE_CONFIRM",
                stake,
                market,
                side,
                line,
                price,
                true);
    }

    private static String confirmedJson(
            String orderId, double stake, String market, String side, double line, double price) {
        return envelope(
                orderId,
                "CONFIRMED",
                "POST_CONFIRM",
                stake,
                market,
                side,
                line,
                price,
                true);
    }

    private static String rejectedJson(String orderId) {
        return "{"
                + "\"envelopeVersion\":\"1\","
                + "\"operatorId\":1,"
                + "\"eventType\":\"OrderRiskCheckEvent\","
                + "\"aggregateId\":\""
                + orderId
                + "\","
                + "\"payload\":{"
                + "\"orderId\":\""
                + orderId
                + "\","
                + "\"status\":\"REJECTED\","
                + "\"phase\":\"POST_CONFIRM\","
                + "\"reasonCode\":\"ORDER_REJECTED\","
                + "\"stake\":1.0,"
                + "\"betTime\":\"2026-06-25T09:59:59.489128Z\","
                + "\"playerId\":\"NQTEST13146949BRL\","
                + "\"legs\":[]"
                + "}}";
    }

    private static String cashedOutJson(String orderId) {
        return "{"
                + "\"envelopeVersion\":\"1\","
                + "\"operatorId\":1,"
                + "\"eventType\":\"OrderRiskCheckEvent\","
                + "\"aggregateId\":\""
                + orderId
                + "\","
                + "\"payload\":{"
                + "\"orderId\":\""
                + orderId
                + "\","
                + "\"status\":\"CASHED_OUT\","
                + "\"phase\":\"POST_CONFIRM\","
                + "\"stake\":100.0,"
                + "\"betTime\":\"2026-06-25T10:00:00Z\","
                + "\"playerId\":\"NQTEST13146949BRL\","
                + "\"legs\":[]"
                + "}}";
    }

    private static String envelope(
            String orderId,
            String status,
            String phase,
            double stake,
            String market,
            String side,
            double line,
            double price,
            boolean withLegs) {
        String legs =
                withLegs
                        ? "\"legs\":[{\"fixtureId\":\""
                                + FIXTURE
                                + "\","
                                + "\"legPick\":{\"type\":\""
                                + market
                                + "\",\"line\":"
                                + line
                                + ",\"side\":\""
                                + side
                                + "\"},"
                                + "\"price\":"
                                + price
                                + "}]"
                        : "\"legs\":[]";
        return "{"
                + "\"envelopeVersion\":\"1\","
                + "\"operatorId\":1,"
                + "\"sourceType\":\"TRADING\","
                + "\"eventType\":\"OrderRiskCheckEvent\","
                + "\"aggregateType\":\"ORDER\","
                + "\"aggregateId\":\""
                + orderId
                + "\","
                + "\"payload\":{"
                + "\"orderId\":\""
                + orderId
                + "\","
                + "\"betType\":\"SINGLE\","
                + "\"status\":\""
                + status
                + "\","
                + "\"phase\":\""
                + phase
                + "\","
                + "\"stake\":"
                + stake
                + ","
                + "\"playerId\":\"NQTEST13146949BRL\","
                + "\"betTime\":\"2026-06-25T12:24:55.305625112Z\","
                + legs
                + "}}";
    }

    private enum Kind {
        PRE,
        POST_CONFIRMED,
        POST_REJECTED,
        POST_CASHED_OUT
    }

    private static final class SimEvent {
        final Kind kind;
        final String label;
        final String orderId;
        final String json;

        SimEvent(Kind kind, String label, String orderId, String json) {
            this.kind = kind;
            this.label = label;
            this.orderId = orderId;
            this.json = json;
        }
    }
}
