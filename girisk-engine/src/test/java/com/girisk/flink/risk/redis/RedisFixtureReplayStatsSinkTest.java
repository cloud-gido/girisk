package com.girisk.flink.risk.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisFixtureReplayStatsSinkTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void countsPassRejectAndDuplicateSeparately() {
        ObjectNode stats = MAPPER.createObjectNode();

        RedisFixtureReplayStatsSink.applyDecisionCounts(stats, true, false, false, false);
        RedisFixtureReplayStatsSink.applyDecisionCounts(stats, true, false, false, false);
        RedisFixtureReplayStatsSink.applyDecisionCounts(stats, true, true, false, false);
        RedisFixtureReplayStatsSink.applyDecisionCounts(stats, false, false, true, false);

        // PASS 非重复不写计数；totalOrders 由 ViewSink 汇总
        assertEquals(0, stats.path("totalOrders").asLong(0));
        assertEquals(1, stats.path("rejectedTotal").asLong());
        assertEquals(1, stats.path("rejectedLimit").asLong());
        assertEquals(1, stats.path("duplicateCount").asLong());
    }
}
