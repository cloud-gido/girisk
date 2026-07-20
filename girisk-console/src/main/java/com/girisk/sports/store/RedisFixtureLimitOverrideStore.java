package com.girisk.sports.store;

import com.girisk.sports.model.FixtureLimitOverride;
import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.outbox.ScopeRiskConfigOutbox;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "girisk.redis.enabled", havingValue = "true")
public class RedisFixtureLimitOverrideStore implements FixtureLimitOverrideStore {

    private static final String KEY_PREFIX = "girisk:override:fixture:";

    private final StringRedisTemplate redis;
    private final ScopeRiskConfigOutbox outbox;

    public RedisFixtureLimitOverrideStore(
            StringRedisTemplate redis, ObjectProvider<ScopeRiskConfigOutbox> outbox) {
        this.redis = redis;
        this.outbox = outbox.getIfAvailable();
    }

    @Override
    public Optional<FixtureLimitOverride> get(String matchCode) {
        if (matchCode == null || matchCode.isBlank()) {
            return Optional.empty();
        }
        Map<Object, Object> hash = redis.opsForHash().entries(KEY_PREFIX + matchCode);
        if (hash == null || hash.isEmpty()) {
            return Optional.empty();
        }
        FixtureLimitOverride o = new FixtureLimitOverride(
                matchCode,
                bd(hash.get("delta")),
                bd(hash.get("seedPayoutYuan")),
                bd(hash.get("maxWorstLossYuan")),
                bd(hash.get("maxBetPayoutYuan")),
                str(hash.get("updatedBy")),
                epoch(hash.get("updatedAt")));
        return o.hasAny() ? Optional.of(o) : Optional.empty();
    }

    @Override
    public void put(FixtureLimitOverride override) {
        String key = KEY_PREFIX + override.matchCode();
        Map<String, String> fields = new HashMap<>();
        putField(fields, "delta", override.delta());
        putField(fields, "seedPayoutYuan", override.seedPayoutYuan());
        putField(fields, "maxWorstLossYuan", override.maxWorstLossYuan());
        putField(fields, "maxBetPayoutYuan", override.maxBetPayoutYuan());
        if (override.updatedBy() != null) {
            fields.put("updatedBy", override.updatedBy());
        }
        Instant at = override.updatedAt() != null ? override.updatedAt() : Instant.now();
        fields.put("updatedAt", String.valueOf(at.toEpochMilli()));
        String matchCode = override.matchCode();
        redis.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<Object> execute(RedisOperations operations) {
                operations.multi();
                operations.delete(key);
                if (!fields.isEmpty()) {
                    operations.opsForHash().putAll(key, fields);
                }
                enqueueTx(operations, matchCode);
                return operations.exec();
            }
        });
    }

    @Override
    public void delete(String matchCode) {
        if (matchCode == null) {
            return;
        }
        String key = KEY_PREFIX + matchCode;
        redis.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<Object> execute(RedisOperations operations) {
                operations.multi();
                operations.delete(key);
                enqueueTx(operations, matchCode);
                return operations.exec();
            }
        });
    }

    @Override
    public List<FixtureLimitOverride> listAll() {
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<FixtureLimitOverride> out = new ArrayList<>();
        for (String redisKey : keys) {
            String matchCode = redisKey.substring(KEY_PREFIX.length());
            get(matchCode).ifPresent(out::add);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void enqueueTx(RedisOperations operations, String matchCode) {
        if (outbox != null && outbox.isRelayEnabled()) {
            outbox.enqueueInTransaction(
                    (RedisOperations<String, String>) operations, LimitScopeType.MATCH, matchCode);
        }
    }

    private static void putField(Map<String, String> fields, String name, BigDecimal v) {
        if (v != null) {
            fields.put(name, v.stripTrailingZeros().toPlainString());
        }
    }

    private static BigDecimal bd(Object v) {
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        return new BigDecimal(s);
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Instant epoch(Object v) {
        if (v == null) {
            return null;
        }
        try {
            long ms = Long.parseLong(v.toString());
            return ms > 0 ? Instant.ofEpochMilli(ms) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}