package com.girisk.sports.store;

import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeLimitOverride;

import java.util.List;
import java.util.Optional;

public interface ScopeLimitOverrideStore {

    Optional<ScopeLimitOverride> get(LimitScopeType type, String scopeKey);

    void put(ScopeLimitOverride override);

    void delete(LimitScopeType type, String scopeKey);

    List<ScopeLimitOverride> listAll();
}
