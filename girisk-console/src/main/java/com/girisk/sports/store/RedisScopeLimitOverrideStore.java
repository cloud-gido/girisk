package com.girisk.sports.store;

import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeGateOverride;
import com.girisk.sports.model.ScopeLimitOverride;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "girisk.redis.enabled", havingValue = "true")
public class RedisScopeLimitOverrideStore implements ScopeLimitOverrideStore {

    private static final String KEY_PREFIX = "girisk:override:scope:";

    private final StringRedisTemplate redis;
    private final ScopeRiskConfigOutbox outbox;

    public RedisScopeLimitOverrideStore(
            StringRedisTemplate redis, ObjectProvider<ScopeRiskConfigOutbox> outbox) {
        this.redis = redis;
        this.outbox = outbox.getIfAvailable();
    }

    @Override
    public Optional<ScopeLimitOverride> get(LimitScopeType type, String scopeKey) {
        if (type == null || scopeKey == null || scopeKey.isBlank()) {
            return Optional.empty();
        }
        Map<Object, Object> hash = redis.opsForHash().entries(redisKey(type, scopeKey));
        if (hash == null || hash.isEmpty()) {
            return Optional.empty();
        }
        ScopeLimitOverride o = new ScopeLimitOverride(
                type,
                normalizeKey(type, scopeKey),
                bd(hash.get("delta")),
                bd(hash.get("seedPayoutYuan")),
                bd(hash.get("maxWorstLossYuan")),
                bd(hash.get("maxBetPayoutYuan")),
                str(hash.get("updatedBy")),
                epoch(hash.get("updatedAt")));
        return o.hasAny() ? Optional.of(o) : Optional.empty();
    }

    @Override
    public void put(ScopeLimitOverride override) {
        String key = redisKey(override.scopeType(), override.scopeKey());
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
        LimitScopeType type = override.scopeType();
        String scopeKey = ScopeGateOverride.normalizeKey(type, override.scopeKey());
        redis.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<Object> execute(RedisOperations operations) {
                operations.multi();
                operations.delete(key);
                if (!fields.isEmpty()) {
                    operations.opsForHash().putAll(key, fields);
                }
                enqueueTx(operations, type, scopeKey);
                return operations.exec();
            }
        });
    }

    @Override
    public void delete(LimitScopeType type, String scopeKey) {
        if (type == null || scopeKey == null) {
            return;
        }
        String key = redisKey(type, scopeKey);
        String normalized = ScopeGateOverride.normalizeKey(type, scopeKey);
        redis.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<Object> execute(RedisOperations operations) {
                operations.multi();
                operations.delete(key);
                enqueueTx(operations, type, normalized);
                return operations.exec();
            }
        });
    }

    @Override
    public List<ScopeLimitOverride> listAll() {
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<ScopeLimitOverride> out = new ArrayList<>();
        for (String redisKey : keys) {
            parseTypeKey(redisKey).ifPresent(tk -> get(tk.type(), tk.key()).ifPresent(out::add));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void enqueueTx(RedisOperations operations, LimitScopeType type, String scopeKey) {
        if (outbox != null && outbox.isRelayEnabled()) {
            outbox.enqueueInTransaction((RedisOperations<String, String>) operations, type, scopeKey);
        }
    }

    static Optional<TypeKey> parseTypeKey(String redisKey) {
        if (redisKey == null || !redisKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        String rest = redisKey.substring(KEY_PREFIX.length());
        int i = rest.indexOf(':');
        if (i <= 0) {
            return Optional.empty();
        }
        try {
            LimitScopeType type = LimitScopeType.valueOf(rest.substring(0, i).toUpperCase(Locale.ROOT));
            return Optional.of(new TypeKey(type, rest.substring(i + 1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    record TypeKey(LimitScopeType type, String key) {}

    static String redisKey(LimitScopeType type, String scopeKey) {
        return KEY_PREFIX + type.name().toLowerCase(Locale.ROOT) + ":" + normalizeKey(type, scopeKey);
    }

    static String normalizeKey(LimitScopeType type, String scopeKey) {
        if (type == LimitScopeType.OVERALL) {
            return "_";
        }
        return scopeKey.trim();
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
        return s.isEmpty() ? null : new BigDecimal(s);
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
