package com.girisk.sports.store;

import com.girisk.sports.model.FixtureLimitOverride;
import com.girisk.sports.model.LimitScopeType;
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
public class InMemoryFixtureLimitOverrideStore implements FixtureLimitOverrideStore {

    private final ConcurrentHashMap<String, FixtureLimitOverride> map = new ConcurrentHashMap<>();
    private final ScopeRiskConfigOutbox outbox;

    public InMemoryFixtureLimitOverrideStore() {
        this.outbox = null;
    }

    @Autowired
    public InMemoryFixtureLimitOverrideStore(ObjectProvider<ScopeRiskConfigOutbox> outbox) {
        this.outbox = outbox.getIfAvailable();
    }

    @Override
    public Optional<FixtureLimitOverride> get(String matchCode) {
        if (matchCode == null || matchCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(matchCode));
    }

    @Override
    public void put(FixtureLimitOverride override) {
        if (override == null || override.matchCode() == null) {
            return;
        }
        map.put(override.matchCode(), override);
        enqueue(override.matchCode());
    }

    @Override
    public void delete(String matchCode) {
        if (matchCode != null) {
            map.remove(matchCode);
            enqueue(matchCode);
        }
    }

    @Override
    public List<FixtureLimitOverride> listAll() {
        return new ArrayList<>(map.values());
    }

    private void enqueue(String matchCode) {
        if (outbox != null && outbox.isRelayEnabled()) {
            outbox.enqueue(LimitScopeType.MATCH, matchCode);
        }
    }
}