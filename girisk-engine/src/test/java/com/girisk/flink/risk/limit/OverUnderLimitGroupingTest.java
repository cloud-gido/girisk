package com.girisk.flink.risk.limit;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.kafka.KafkaFootballOrderCsvParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverUnderLimitGroupingTest {

    @Test
    void usesIndustrySelectionKeys() throws Exception {
        FootballSportsOrder over =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O1,2026-05-18 10:00:00,U1,英超,曼城,利物浦,2026-05-16 22:00:00,大小球,单关,3,大球,1.85,100");

        Optional<LimitSelectionResolver.ResolvedOutcome> resolved = LimitSelectionResolver.resolve(over);
        assertTrue(resolved.isPresent());
        assertEquals(LimitMarketType.OVER_UNDER, resolved.get().marketType);
        assertEquals("over", resolved.get().selectionKey);
        assertEquals("3", resolved.get().line);

        List<MarketStakeAggregator.MarketGroupLimit> groups =
                MarketStakeAggregator.aggregate(List.of(over), 0.2);
        assertEquals("over", groups.get(0).rows.get(0).selection);
        assertEquals("Over 3", groups.get(0).rows.get(0).selectionLabel);
        assertEquals("under", groups.get(0).rows.get(1).selection);
        assertEquals("Under 3", groups.get(0).rows.get(1).selectionLabel);
    }
}
