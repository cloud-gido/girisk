package com.girisk.sports.store;

import com.girisk.sports.model.FixtureLimitOverride;

import java.util.Optional;

public interface FixtureLimitOverrideStore {

    Optional<FixtureLimitOverride> get(String matchCode);

    void put(FixtureLimitOverride override);

    void delete(String matchCode);
}
