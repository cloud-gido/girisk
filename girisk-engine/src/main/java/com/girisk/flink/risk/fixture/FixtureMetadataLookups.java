package com.girisk.flink.risk.fixture;

import com.girisk.flink.support.util.CliParameterTool;

import java.nio.file.Path;
import java.util.Map;

/** 组装赛事维表 Lookup。 */
public final class FixtureMetadataLookups {

    private FixtureMetadataLookups() {}

    public static FixtureMetadataLookup from(CliParameterTool t) {
        if (t.has("fixture.dim.file")) {
            return new FileFixtureMetadataLookup(Path.of(t.get("fixture.dim.file").trim()));
        }
        return new MapFixtureMetadataLookup(Map.of());
    }
}
