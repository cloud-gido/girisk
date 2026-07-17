package com.girisk.flink.risk.limit;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.kafka.KafkaFootballOrderCsvParser;
import com.girisk.flink.risk.kafka.MatchExposureSummaryJson;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 先限额（Gate 1，返彩 + 种子）后敞口（Gate 2）的两道闸门决策测试。 */
class MatchTriggerAcceptanceTest {

    private static final ScoreGridParams GRID =
            ScoreGridParams.fromMap(Map.of("score", "0:0", "grid", "2"));

    @Test
    void gate1LimitRejected_excludedFromSummaryExposure() throws Exception {
        // 无种子：主胜已 185 返彩独占，b_max=0，本笔返彩 925 >= 0 → LIMIT 拒
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

        assertTrue(acceptance.limitRejected);
        assertFalse(acceptance.exposureRejected);
        assertEquals(MatchTriggerAcceptance.RejectReason.LIMIT, acceptance.rejectReason);
        assertTrue(acceptance.shouldReject);
        assertTrue(acceptance.triggerRejected);
        assertFalse(acceptance.persistTrigger());
        assertEquals(1, acceptance.acceptedOrders.size());
        assertEquals(2, acceptance.trialOrdersIncludingTrigger.size());

        var trialExposure =
                MatchExposureAggregator.summarize(acceptance.trialOrdersIncludingTrigger, GRID.grid);
        var summaryExposure =
                MatchExposureAggregator.summarize(acceptance.acceptedOrders, GRID.grid);
        assertTrue(
                MatchExposureSummaryJson.maxProfitMeta(trialExposure.scenarios).maxProfitCents
                        > MatchExposureSummaryJson.maxProfitMeta(summaryExposure.scenarios)
                                .maxProfitCents);
    }

    @Test
    void gate2ExposureRejected_whenWorstLossExceedsThreshold() throws Exception {
        // 种子拉大让 Gate 1 通过（b_max=33333>3700），最差净亏 2000×0.85=1700 > 1000 → EXPOSURE 拒
        FootballSportsOrder trigger =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O20,2026-05-18 10:12:00,U2,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,2000");
        EnrichedFootballOrder enriched = new EnrichedFootballOrder(trigger, 1L, "k");

        MatchTriggerAcceptance acceptance =
                MatchTriggerAcceptance.evaluate(
                        List.of(), enriched, false, GRID.grid, 0.2, 100000.0, 1000.0, false);

        assertFalse(acceptance.limitRejected);
        assertTrue(acceptance.exposureRejected);
        assertEquals(MatchTriggerAcceptance.RejectReason.EXPOSURE, acceptance.rejectReason);
        assertTrue(acceptance.triggerRejected);
        assertFalse(acceptance.persistTrigger());
    }

    @Test
    void bothGatesPass_triggerAccepted() {
        FootballSportsOrder trigger =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O3,2026-05-18 10:12:00,U2,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,50");
        EnrichedFootballOrder enriched = new EnrichedFootballOrder(trigger, 1L, "k");

        MatchTriggerAcceptance acceptance =
                MatchTriggerAcceptance.evaluate(
                        List.of(), enriched, false, GRID.grid, 0.2, 2000.0, 12000.0, false);

        assertEquals(MatchTriggerAcceptance.RejectReason.NONE, acceptance.rejectReason);
        assertFalse(acceptance.shouldReject);
        assertFalse(acceptance.triggerRejected);
        assertTrue(acceptance.persistTrigger());
        assertEquals(1, acceptance.acceptedOrders.size());
    }

    @Test
    void coldStartSeed_firstOrderCapacityMatchesClosedForm() {
        // 种子 2000、胜平负三向：首单容量 = (0.4×6000−2000)/0.6 = 666.67（>= 拒）
        FootballSportsOrder accept = order("O30", 666, 1.0);
        FootballSportsOrder reject = order("O31", 667, 1.0);

        MatchTriggerAcceptance ok =
                MatchTriggerAcceptance.evaluate(
                        List.of(),
                        new EnrichedFootballOrder(accept, 1L, "k"),
                        false,
                        GRID.grid,
                        0.2,
                        2000.0,
                        ExposureLimitGate.WORST_LOSS_DISABLED,
                        true);
        MatchTriggerAcceptance blocked =
                MatchTriggerAcceptance.evaluate(
                        List.of(),
                        new EnrichedFootballOrder(reject, 1L, "k"),
                        false,
                        GRID.grid,
                        0.2,
                        2000.0,
                        ExposureLimitGate.WORST_LOSS_DISABLED,
                        true);

        assertEquals(MatchTriggerAcceptance.RejectReason.NONE, ok.rejectReason);
        assertEquals(MatchTriggerAcceptance.RejectReason.LIMIT, blocked.rejectReason);
    }

    private static FootballSportsOrder order(String orderId, long stakeYuan, double odds) {
        FootballSportsOrder o = new FootballSportsOrder();
        o.fixtureId = "13883500";
        o.orderId = orderId;
        o.orderTime = "2026-05-18 10:12:00";
        o.playType = "胜平负";
        o.parlayType = "单关";
        o.handicapText = "无";
        o.selection = "主胜";
        o.odds = odds;
        o.stakeYuan = stakeYuan;
        return o;
    }
}
