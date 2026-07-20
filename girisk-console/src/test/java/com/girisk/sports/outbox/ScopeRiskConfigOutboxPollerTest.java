package com.girisk.sports.outbox;

import com.girisk.config.RiskKafkaProperties;
import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.service.ScopeRiskConfigDispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeRiskConfigOutboxPollerTest {

    @Test
    void pollPublishesAndDrains() {
        RiskKafkaProperties props = new RiskKafkaProperties();
        props.setEnabled(true);
        props.setConfigOutboxEnabled(true);
        props.setConfigOutboxBatchSize(10);
        props.setConfigOutboxMaxAttempts(5);

        InMemoryScopeRiskConfigOutbox outbox = new InMemoryScopeRiskConfigOutbox(props);
        outbox.enqueue(LimitScopeType.LEAGUE, "football:L1");
        outbox.enqueue(LimitScopeType.LEAGUE, "football:L1"); // coalesce in batch
        outbox.enqueue(LimitScopeType.SPORT, "football");

        AtomicInteger published = new AtomicInteger();
        List<String> keys = new ArrayList<>();
        ScopeRiskConfigDispatchService dispatch = new ScopeRiskConfigDispatchService(
                null, null, null, emptyPublisher(), emptyOutbox()) {
            @Override
            public void publishScopeSnapshot(LimitScopeType type, String scopeKey) {
                published.incrementAndGet();
                keys.add(type.name() + ":" + scopeKey);
            }
        };

        ScopeRiskConfigOutboxPoller poller =
                new ScopeRiskConfigOutboxPoller(outbox, dispatch, props, emptyEvents());
        poller.poll();

        assertEquals(2, published.get());
        assertTrue(keys.contains("LEAGUE:football:L1"));
        assertTrue(keys.contains("SPORT:football"));
        assertEquals(0, outbox.pendingDepth());
    }

    @Test
    void failedPublishRequeuesUntilDlq() {
        RiskKafkaProperties props = new RiskKafkaProperties();
        props.setEnabled(true);
        props.setConfigOutboxEnabled(true);
        props.setConfigOutboxBatchSize(1); // 避免同轮立刻把 requeue 再 claim 光
        props.setConfigOutboxMaxAttempts(2);

        InMemoryScopeRiskConfigOutbox outbox = new InMemoryScopeRiskConfigOutbox(props);
        outbox.enqueue(LimitScopeType.MATCH, "m1");

        ScopeRiskConfigDispatchService dispatch = new ScopeRiskConfigDispatchService(
                null, null, null, emptyPublisher(), emptyOutbox()) {
            @Override
            public void publishScopeSnapshot(LimitScopeType type, String scopeKey) {
                throw new RuntimeException("kafka down");
            }
        };

        ScopeRiskConfigOutboxPoller poller =
                new ScopeRiskConfigOutboxPoller(outbox, dispatch, props, emptyEvents());
        poller.poll(); // attempt 0 → requeue as 1
        assertEquals(1, outbox.pendingDepth());
        poller.poll(); // attempt 1 → DLQ (1+1 >= 2)
        assertEquals(0, outbox.pendingDepth());
        assertEquals(1, outbox.drainDlqForTest().size());
    }

    private static ObjectProvider<com.girisk.flink.ScopeRiskConfigKafkaPublisher> emptyPublisher() {
        return new ObjectProvider<>() {
            @Override
            public com.girisk.flink.ScopeRiskConfigKafkaPublisher getObject() {
                return null;
            }

            @Override
            public com.girisk.flink.ScopeRiskConfigKafkaPublisher getObject(Object... args) {
                return null;
            }

            @Override
            public com.girisk.flink.ScopeRiskConfigKafkaPublisher getIfAvailable() {
                return null;
            }

            @Override
            public com.girisk.flink.ScopeRiskConfigKafkaPublisher getIfUnique() {
                return null;
            }
        };
    }

    private static ObjectProvider<ScopeRiskConfigOutbox> emptyOutbox() {
        return new ObjectProvider<>() {
            @Override
            public ScopeRiskConfigOutbox getObject() {
                return null;
            }

            @Override
            public ScopeRiskConfigOutbox getObject(Object... args) {
                return null;
            }

            @Override
            public ScopeRiskConfigOutbox getIfAvailable() {
                return null;
            }

            @Override
            public ScopeRiskConfigOutbox getIfUnique() {
                return null;
            }
        };
    }

    private static ObjectProvider<com.girisk.event.repository.RiskEventRepository> emptyEvents() {
        return new ObjectProvider<>() {
            @Override
            public com.girisk.event.repository.RiskEventRepository getObject() {
                return null;
            }

            @Override
            public com.girisk.event.repository.RiskEventRepository getObject(Object... args) {
                return null;
            }

            @Override
            public com.girisk.event.repository.RiskEventRepository getIfAvailable() {
                return null;
            }

            @Override
            public com.girisk.event.repository.RiskEventRepository getIfUnique() {
                return null;
            }
        };
    }
}
