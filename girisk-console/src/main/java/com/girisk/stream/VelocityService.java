package com.girisk.stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "girisk.redis.enabled", havingValue = "true")
public class VelocityService implements VelocityCounter {

    private static final String COUNT_PREFIX = "risk:velocity:count:";
    private static final String AMOUNT_PREFIX = "risk:velocity:amount:";

    private final StringRedisTemplate redis;
    private final long ttlHours;

    public VelocityService(StringRedisTemplate redis, @Value("${girisk.redis.velocity-ttl-hours:24}") long ttlHours) {
        this.redis = redis;
        this.ttlHours = ttlHours;
    }

    public int recordAndGetCount(String userId) {
        String key = COUNT_PREFIX + userId;
        Long count = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofHours(ttlHours));
        return count != null ? count.intValue() : 1;
    }

    public BigDecimal recordAndGetAmountSum(String userId, BigDecimal amount) {
        String key = AMOUNT_PREFIX + userId;
        Double sum = redis.opsForValue().increment(key, amount.doubleValue());
        redis.expire(key, ttlHours, TimeUnit.HOURS);
        return BigDecimal.valueOf(sum != null ? sum : amount.doubleValue());
    }

    public int getCount(String userId) {
        String val = redis.opsForValue().get(COUNT_PREFIX + userId);
        return val != null ? Integer.parseInt(val) : 0;
    }

    public BigDecimal getAmountSum(String userId) {
        String val = redis.opsForValue().get(AMOUNT_PREFIX + userId);
        return val != null ? new BigDecimal(val) : BigDecimal.ZERO;
    }
}
