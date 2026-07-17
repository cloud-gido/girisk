package com.girisk.sports.store;

import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeLimitOverride;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "girisk.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryScopeLimitOverrideStore implements ScopeLimitOverrideStore {

    private final ConcurrentHashMap<String, ScopeLimitOverride> map = new ConcurrentHashMap<>();

    @Override
    public Optional<ScopeLimitOverride> get(LimitScopeType type, String scopeKey) {
        return Optional.ofNullable(map.get(RedisScopeLimitOverrideStore.redisKey(type, scopeKey)));
    }

    @Override
    public void put(ScopeLimitOverride override) {
        map.put(RedisScopeLimitOverrideStore.redisKey(override.scopeType(), override.scopeKey()), override);
    }

    @Override
    public void delete(LimitScopeType type, String scopeKey) {
        map.remove(RedisScopeLimitOverrideStore.redisKey(type, scopeKey));
    }
}
