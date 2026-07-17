package com.girisk.flink.risk.model;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.kafka.KafkaFootballOrderCsvParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchKeysTest {

    @Test
    void matchKeyIncludesFixtureIdFirst() {
        FootballSportsOrder o =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,ORD1,2026-05-18 10:00:00,U1,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,100");
        assertEquals(
                "13883500|英超|曼城|利物浦|2026-05-16 22:00:00",
                MatchKeys.of(o));
    }
}
