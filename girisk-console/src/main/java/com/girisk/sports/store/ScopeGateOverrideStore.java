package com.girisk.sports.store;

import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeGateOverride;

import java.util.List;
import java.util.Optional;

public interface ScopeGateOverrideStore {

    Optional<ScopeGateOverride> get(LimitScopeType type, String scopeKey);

    void put(ScopeGateOverride override);

    void delete(LimitScopeType type, String scopeKey);

    /** 全量列举（启动同步 / 运维重刷）。 */
    List<ScopeGateOverride> listAll();
}
