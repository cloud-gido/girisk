package com.girisk.gateway;

import com.girisk.sports.model.MarketGroupKey;
import com.girisk.sports.model.SportsMarketType;
import com.girisk.sports.store.InMemoryExposureStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderRiskStateServiceTest {

    private OrderRiskStateService service;
    private InMemoryExposureStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryExposureStore();
        service = new OrderRiskStateService(store);
    }

    @Test
    void confirmCancelSettleFlow() {
        MarketGroupKey key = MarketGroupKey.of("MATCH-001", SportsMarketType.ONE_X_TWO, null);
        assertTrue(store.tryReserve(
                "ORD-1", key, "home",
                new BigDecimal("100"), new BigDecimal("210"),
                new BigDecimal("10000"), 30).isEmpty());

        Map<String, Object> confirmed = service.confirm("ORD-1");
        assertEquals("CONFIRMED", confirmed.get("status"));

        Map<String, Object> settled = service.settle("ORD-1", 50L);
        assertEquals("SETTLED", settled.get("status"));
        assertEquals(50L, settled.get("settlePnlCents"));

        Map<String, Object> status = service.status("ORD-1");
        assertEquals("SETTLED", status.get("status"));
        assertEquals(50L, status.get("settlePnlCents"));
    }

    @Test
    void cancelReleasesPending() {
        MarketGroupKey key = MarketGroupKey.of("MATCH-001", SportsMarketType.ONE_X_TWO, null);
        store.tryReserve(
                "ORD-2", key, "away",
                new BigDecimal("50"), new BigDecimal("100"),
                new BigDecimal("10000"), 30);
        Map<String, Object> cancelled = service.cancel("ORD-2");
        assertEquals("CANCELLED", cancelled.get("status"));
        assertTrue((Boolean) cancelled.get("released"));
        assertTrue(store.getReserve("ORD-2").isEmpty());
    }
}
