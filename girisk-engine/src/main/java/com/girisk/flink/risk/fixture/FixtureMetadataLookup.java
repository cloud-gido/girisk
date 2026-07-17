package com.girisk.flink.risk.fixture;

import java.io.Serializable;
import java.util.Optional;

/** 按 {@code fixtureId} 查询赛事维表。 */
public interface FixtureMetadataLookup extends Serializable {

    Optional<FixtureMetadata> find(String fixtureId);

    /** 维表热更新（文件维表 reload 等）；默认无操作。 */
    default void refresh() {}
}
