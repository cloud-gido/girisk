package com.girisk.sports.store;

import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeGateOverride;
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
public class InMemoryScopeGateOverrideStore implements ScopeGateOverrideStore {

    private final ConcurrentHashMap<String, ScopeGateOverride> map = new ConcurrentHashMap<>();
    private final ScopeRiskConfigOutbox outbox;

    public InMemoryScopeGateOverrideStore() {
        this.outbox = null;
    }

    @Autowired
    public InMemoryScopeGateOverrideStore(ObjectProvider<ScopeRiskConfigOutbox> outbox) {
        this.outbox = outbox.getIfAvailable();
    }

    @Override
    public Optional<ScopeGateOverride> get(LimitScopeType type, String scopeKey) {
        return Optional.ofNullable(map.get(RedisScopeGateOverrideStore.redisKey(type, scopeKey)));
    }

    @Override
    public void put(ScopeGateOverride override) {
        map.put(RedisScopeGateOverrideStore.redisKey(override.scopeType(), override.scopeKey()), override);
        enqueue(override.scopeType(), override.scopeKey());
    }

    @Override
    public void delete(LimitScopeType type, String scopeKey) {
        map.remove(RedisScopeGateOverrideStore.redisKey(type, scopeKey));
        enqueue(type, scopeKey);
    }

    @Override
    public List<ScopeGateOverride> listAll() {
        return new ArrayList<>(map.values());
    }

    private void enqueue(LimitScopeType type, String scopeKey) {
        if (outbox != null && outbox.isRelayEnabled()) {
            outbox.enqueue(type, scopeKey);
        }
    }
}
