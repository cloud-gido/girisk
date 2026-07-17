package com.girisk.flink.risk.limit;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.kafka.KafkaFootballOrderCsvParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExposureLimitGateTest {

    // 主胜 100@1.85：主队赢时平台净亏 85 元
    private static MatchExposureAggregator.ExposureSummary exposure() {
        FootballSportsOrder o =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O1,2026-05-18 10:00:00,U1,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,100");
        return MatchExposureAggregator.summarize(
                List.of(o),
                ScoreGridParams.fromMap(java.util.Map.of("score", "0:0", "grid", "6")).grid);
    }

    @Test
    void worstLossBelowThreshold_notRejected() {
        assertFalse(ExposureLimitGate.exceedsWorstLoss(exposure(), 1000.0));
    }

    @Test
    void worstLossAboveThreshold_rejected() {
        assertTrue(ExposureLimitGate.exceedsWorstLoss(exposure(), 50.0));
    }

    @Test
    void negativeThresholdTreatedAsAbs_sameAsProductCalculator() {
        assertTrue(ExposureLimitGate.exceedsWorstLoss(exposure(), -50.0));
        assertFalse(ExposureLimitGate.exceedsWorstLoss(exposure(), -1000.0));
    }

    @Test
    void disabled_neverRejects() {
        assertFalse(
                ExposureLimitGate.exceedsWorstLoss(
                        exposure(), ExposureLimitGate.WORST_LOSS_DISABLED));
    }

    @Test
    void exactThresholdNotRejected_strictLessThanSemantics() {
        // 最差净亏恰等于阈值（85）：产品口径为「< -阈值」严格判定，临界不拒
        assertFalse(ExposureLimitGate.exceedsWorstLoss(exposure(), 85.0));
    }
}
