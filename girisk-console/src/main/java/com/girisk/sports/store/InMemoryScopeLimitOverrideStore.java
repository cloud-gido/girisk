package com.girisk.sports.store;

import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeLimitOverride;
import com.girisk.sports.outbox.ScopeRiskConfigOutbox;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "girisk.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryScopeLimitOverrideStore implements ScopeLimitOverrideStore {

    private final ConcurrentHashMap<String, ScopeLimitOverride> map = new ConcurrentHashMap<>();
    private final ScopeRiskConfigOutbox outbox;

    public InMemoryScopeLimitOverrideStore() {
        this.outbox = null;
    }

    @Autowired
    public InMemoryScopeLimitOverrideStore(ObjectProvider<ScopeRiskConfigOutbox> outbox) {
        this.outbox = outbox.getIfAvailable();
    }

    @Override
    public Optional<ScopeLimitOverride> get(LimitScopeType type, String scopeKey) {
        return Optional.ofNullable(map.get(RedisScopeLimitOverrideStore.redisKey(type, scopeKey)));
    }

    @Override
    public void put(ScopeLimitOverride override) {
        map.put(RedisScopeLimitOverrideStore.redisKey(override.scopeType(), override.scopeKey()), override);
        enqueue(override.scopeType(), override.scopeKey());
    }

    @Override
    public void delete(LimitScopeType type, String scopeKey) {
        map.remove(RedisScopeLimitOverrideStore.redisKey(type, scopeKey));
        enqueue(type, scopeKey);
    }

    @Override
    public List<ScopeLimitOverride> listAll() {
        return new ArrayList<>(map.values());
    }

    private void enqueue(LimitScopeType type, String scopeKey) {
        if (outbox != null && outbox.isRelayEnabled()) {
            outbox.enqueue(type, scopeKey);
        }
    }
}
