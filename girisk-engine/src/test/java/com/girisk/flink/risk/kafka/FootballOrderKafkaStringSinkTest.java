package com.girisk.flink.risk.kafka;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FootballOrderKafkaStringSinkTest {

    @Test
    void transactionalIdPrefixIsUniquePerTopic() {
        String summary = FootballOrderKafkaStringSink.transactionalIdPrefix("girisk.football.summary.result");
        String limit = FootballOrderKafkaStringSink.transactionalIdPrefix("girisk.football.limit.result");
        assertNotEquals(summary, limit);
        assertEquals("flink-football-girisk.football.summary.result", summary);
        assertEquals("flink-football-girisk.football.limit.result", limit);
    }

    @Test
    void transactionalIdPrefixSanitizesInvalidChars() {
        assertEquals(
                "flink-football-football_order_risk",
                FootballOrderKafkaStringSink.transactionalIdPrefix("Football Order/Risk"));
    }
}
