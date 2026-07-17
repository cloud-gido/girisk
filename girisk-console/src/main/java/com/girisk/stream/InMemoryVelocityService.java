package com.girisk.stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@ConditionalOnProperty(name = "girisk.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryVelocityService implements VelocityCounter {

    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<BigDecimal>> amounts = new ConcurrentHashMap<>();

    @Override
    public int recordAndGetCount(String userId) {
        return counts.computeIfAbsent(userId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    @Override
    public BigDecimal recordAndGetAmountSum(String userId, BigDecimal amount) {
        AtomicReference<BigDecimal> ref = amounts.computeIfAbsent(userId, k -> new AtomicReference<>(BigDecimal.ZERO));
        ref.updateAndGet(v -> v.add(amount));
        return ref.get();
    }
}
