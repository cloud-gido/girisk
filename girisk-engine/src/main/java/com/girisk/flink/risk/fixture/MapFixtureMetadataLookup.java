package com.girisk.flink.risk.fixture;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 内存维表（测试 / 与文件维表合并）。 */
public final class MapFixtureMetadataLookup implements FixtureMetadataLookup {
    private static final long serialVersionUID = 1L;

    private final Map<String, FixtureMetadata> byId;

    public MapFixtureMetadataLookup(Map<String, FixtureMetadata> byId) {
        this.byId = Collections.unmodifiableMap(new HashMap<>(byId));
    }

    @Override
    public Optional<FixtureMetadata> find(String fixtureId) {
        if (fixtureId == null || fixtureId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(fixtureId.trim()));
    }
}
