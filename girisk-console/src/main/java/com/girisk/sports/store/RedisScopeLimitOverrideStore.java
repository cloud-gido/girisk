package com.girisk.sports.store;

import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeLimitOverride;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "girisk.redis.enabled", havingValue = "true")
public class RedisScopeLimitOverrideStore implements ScopeLimitOverrideStore {

    private static final String KEY_PREFIX = "girisk:override:scope:";

    private final StringRedisTemplate redis;

    public RedisScopeLimitOverrideStore(StringRedisTemplate redis) {
        this.redis = redis;
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
        redis.delete(key);
        if (!fields.isEmpty()) {
            redis.opsForHash().putAll(key, fields);
        }
    }

    @Override
    public void delete(LimitScopeType type, String scopeKey) {
        if (type != null && scopeKey != null) {
            redis.delete(redisKey(type, scopeKey));
        }
    }

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
