package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskKafkaMessageIdsTest {

    @Test
    void newEventIdIsUuidAndUnique() {
        String a = RiskKafkaMessageIds.newEventId();
        String b = RiskKafkaMessageIds.newEventId();
        assertNotEquals(a, b);
        UUID.fromString(a);
        UUID.fromString(b);

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            assertTrue(ids.add(RiskKafkaMessageIds.newEventId()));
        }
    }

    @Test
    void upstreamEventIdFromOrder() {
        FootballSportsOrder o = new FootballSportsOrder();
        o.eventId = "evt-abc";
        assertEquals("evt-abc", RiskKafkaMessageIds.upstreamEventId(o));
        o.eventId = "";
        assertEquals("", RiskKafkaMessageIds.upstreamEventId(o));
    }
}
