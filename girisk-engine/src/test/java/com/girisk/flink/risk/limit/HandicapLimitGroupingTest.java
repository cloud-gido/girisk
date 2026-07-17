package com.girisk.flink.risk.limit;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.kafka.KafkaFootballOrderCsvParser;
import com.girisk.flink.risk.settlement.BetMarketFamily;
import com.girisk.flink.risk.settlement.PlayTypeRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandicapLimitGroupingTest {

    @Test
    void asianHandicapAggregatesAsBigSmallPair() throws Exception {
        FootballSportsOrder home =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O1,2026-05-18 10:00:00,U1,英超,曼城,利物浦,2026-05-16 22:00:00,让球,单关,主队+1,主胜,1.85,150");
        FootballSportsOrder away =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O2,2026-05-18 10:01:00,U2,英超,曼城,利物浦,2026-05-16 22:00:00,让球,单关,主队+1,客胜,2.10,0");

        assertEquals(BetMarketFamily.ASIAN_HANDICAP, PlayTypeRegistry.resolve(home));

        List<MarketStakeAggregator.MarketGroupLimit> groups =
                MarketStakeAggregator.aggregate(List.of(home, away), 0.2);
        assertEquals(1, groups.size());

        MarketStakeAggregator.MarketGroupLimit g = groups.get(0);
        assertEquals(LimitMarketType.HANDICAP, g.marketType);
        assertEquals("让球", g.marketLabel);
        assertEquals("HANDICAP|1", g.groupKey);
        // 返彩口径：150 × 1.85 = 277.50
        assertEquals(0, g.groupTotalStake.compareTo(new BigDecimal("277.50")));
        assertEquals(2, g.rows.size());
        assertEquals("home", g.rows.get(0).selection);
        assertEquals("+1", g.rows.get(0).selectionLabel);
        assertEquals("away", g.rows.get(1).selection);
        assertEquals("-1", g.rows.get(1).selectionLabel);
        assertTrue(g.rows.get(0).overLimit);
    }

    @Test
    void handicapThreeWayWinLossMapsToTwoWayGroup() throws Exception {
        FootballSportsOrder homeWin =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O3,2026-05-18 10:29:00,U3,英超,曼城,利物浦,2026-05-16 22:00:00,让球胜平负,单关,主队 -0.25,主队让胜,2.35,50");

        Optional<LimitSelectionResolver.ResolvedOutcome> resolved =
                LimitSelectionResolver.resolve(homeWin);
        assertTrue(resolved.isPresent());
        assertEquals(LimitMarketType.HANDICAP, resolved.get().marketType);
        assertEquals("home", resolved.get().selectionKey);
        assertEquals("-0.25", resolved.get().line);

        List<MarketStakeAggregator.MarketGroupLimit> groups =
                MarketStakeAggregator.aggregate(List.of(homeWin), 0.2);
        assertEquals(1, groups.size());
        MarketStakeAggregator.MarketGroupLimit g = groups.get(0);
        assertEquals(LimitMarketType.HANDICAP, g.marketType);
        assertEquals("home", g.rows.get(0).selection);
        assertEquals("-0.25", g.rows.get(0).selectionLabel);
        assertEquals("away", g.rows.get(1).selection);
        assertEquals("+0.25", g.rows.get(1).selectionLabel);
    }
}
