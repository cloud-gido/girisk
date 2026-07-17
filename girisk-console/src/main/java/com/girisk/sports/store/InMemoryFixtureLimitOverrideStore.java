package com.girisk.sports.store;

import com.girisk.sports.model.FixtureLimitOverride;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "girisk.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryFixtureLimitOverrideStore implements FixtureLimitOverrideStore {

    private final ConcurrentHashMap<String, FixtureLimitOverride> map = new ConcurrentHashMap<>();

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
    }

    @Override
    public void delete(String matchCode) {
        if (matchCode != null) {
            map.remove(matchCode);
        }
    }
}
