package com.girisk.flink.risk;

import com.girisk.flink.risk.time.OrderEventTimes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchExposureEventTimeCleanupTest {

    private static final long THREE_HOURS_MS = 3L * 3_600_000L;

    @Test
    void orderBeforeCutoffAcceptedByEventTime() {
        long kickoffMs = OrderEventTimes.parseKickoffTimeMillis("2026-05-20 20:00:00");
        long cutoffMs = kickoffMs + THREE_HOURS_MS;
        long orderTimeMs = OrderEventTimes.parseOrderTimeMillis("2026-05-18 10:37:00");
        assertTrue(orderTimeMs <= cutoffMs);
    }

    @Test
    void orderAfterCutoffRejectedByEventTime() {
        long kickoffMs = OrderEventTimes.parseKickoffTimeMillis("2026-05-20 20:00:00");
        long cutoffMs = kickoffMs + THREE_HOURS_MS;
        long orderTimeMs = OrderEventTimes.parseOrderTimeMillis("2026-05-20 23:30:00");
        assertFalse(orderTimeMs <= cutoffMs);
    }
}
