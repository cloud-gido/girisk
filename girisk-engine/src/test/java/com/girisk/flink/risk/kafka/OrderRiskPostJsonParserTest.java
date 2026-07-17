package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.model.OrderPostStatus;
import com.girisk.flink.risk.model.OrderPostStatusUpdate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderRiskPostJsonParserTest {

    private static final String CONFIRMED =
            "{"
                    + "\"eventType\":\"OrderRiskCheckEvent\","
                    + "\"aggregateId\":\"328506241570066433\","
                    + "\"payload\":{"
                    + "\"orderId\":\"328506241570066433\","
                    + "\"status\":\"CONFIRMED\","
                    + "\"phase\":\"POST_CONFIRM\","
                    + "\"confirmedAt\":\"2026-06-25T12:06:38.332116Z\","
                    + "\"stake\":3.0,"
                    + "\"betTime\":\"2026-06-25T12:06:31.348926Z\","
                    + "\"playerId\":\"NQTEST13146949BRL\","
                    + "\"legs\":[{\"fixtureId\":\"13999839\","
                    + "\"legPick\":{\"type\":\"OU\",\"line\":3.0,\"side\":\"OVER\"},"
                    + "\"price\":2.15}]}}";

    private static final String REJECTED =
            "{"
                    + "\"eventType\":\"OrderRiskCheckEvent\","
                    + "\"aggregateId\":\"328474399034564609\","
                    + "\"payload\":{"
                    + "\"orderId\":\"328474399034564609\","
                    + "\"status\":\"REJECTED\","
                    + "\"phase\":\"POST_CONFIRM\","
                    + "\"stake\":1.0,"
                    + "\"betTime\":\"2026-06-25T09:59:59.489128Z\","
                    + "\"playerId\":\"NQTEST13146949BRL\","
                    + "\"legs\":[]}}";

    @Test
    void parseConfirmed() {
        OrderPostStatusUpdate update = OrderRiskPostJsonParser.parse(CONFIRMED);
        assertEquals(OrderPostStatus.CONFIRMED, update.status);
        assertEquals("328506241570066433", update.orderId);
        assertEquals("13999839", update.fixtureId);
        assertNotNull(update.order);
        assertEquals("大球", update.order.selection);
    }

    @Test
    void parseRejectedWithoutLegs() {
        OrderPostStatusUpdate update = OrderRiskPostJsonParser.parse(REJECTED);
        assertEquals(OrderPostStatus.REJECTED, update.status);
        assertEquals("328474399034564609", update.orderId);
        assertEquals("", update.fixtureId);
        assertNull(update.order);
    }

    @Test
    void unifiedParserAcceptsPostStatuses() {
        assertTrue(FootballOrderUnifiedParser.tryParsePostStatus(CONFIRMED).isOk());
        assertTrue(FootballOrderUnifiedParser.tryParsePostStatus(REJECTED).isOk());
    }
}
