package com.girisk.flink.risk.limit;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.kafka.KafkaFootballOrderCsvParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketStakeAggregatorTest {

    /** 产品文档 1X2 算例（赔率 1.0 使返彩=本金）：10000/3000/2000 → 0 / 5000 / 6666.67。 */
    @Test
    void aggregatesOneXTwo_matchesProductExample() {
        FootballSportsOrder home = order("O1", "主胜", 10000, 1.0);
        FootballSportsOrder draw = order("O2", "平局", 3000, 1.0);
        FootballSportsOrder away = order("O3", "客胜", 2000, 1.0);

        List<MarketStakeAggregator.MarketGroupLimit> groups =
                MarketStakeAggregator.aggregate(List.of(home, draw, away), 0.2);
        assertEquals(1, groups.size());
        MarketStakeAggregator.MarketGroupLimit g = groups.get(0);
        assertEquals(LimitMarketType.ONE_X_TWO, g.marketType);
        assertEquals(0, g.groupTotalStake.compareTo(new BigDecimal("15000")));

        assertEquals(0, findAcceptMax(g, "away").compareTo(new BigDecimal("6666.67")));
        assertEquals(0, findAcceptMax(g, "draw").compareTo(new BigDecimal("5000")));
        assertEquals(0, findAcceptMax(g, "home").compareTo(BigDecimal.ZERO));
        assertTrue(findOver(g, "home"));
    }

    /** 返彩口径：金额 = 投注金额 × 赔率。 */
    @Test
    void amountsArePayoutNotStake() {
        FootballSportsOrder home =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O1,2026-05-18 10:00:00,U1,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,1000");

        List<MarketStakeAggregator.MarketGroupLimit> groups =
                MarketStakeAggregator.aggregate(List.of(home), 0.2);
        MarketStakeAggregator.MarketGroupLimit g = groups.get(0);
        assertEquals(0, findStake(g, "home").compareTo(new BigDecimal("1850.00")));
        assertEquals(0, g.groupTotalStake.compareTo(new BigDecimal("1850.00")));
    }

    /** 冷启动种子：空窗口 + ensureGroupFor 建组，每盘口记入种子，首单容量 = 种子/3。 */
    @Test
    void coldStartSeedEnsuresTriggerGroup() {
        FootballSportsOrder trigger = order("O1", "主胜", 100, 1.38);

        List<MarketStakeAggregator.MarketGroupLimit> groups =
                MarketStakeAggregator.aggregate(List.of(), 0.2, 2000.0, trigger);
        assertEquals(1, groups.size());
        MarketStakeAggregator.MarketGroupLimit g = groups.get(0);
        assertEquals(LimitMarketType.ONE_X_TWO, g.marketType);
        assertEquals(0, g.groupTotalStake.compareTo(new BigDecimal("6000")));
        assertEquals(0, findStake(g, "home").compareTo(new BigDecimal("2000")));
        // (0.4×6000−2000)/0.6 = 666.67
        assertEquals(0, findAcceptMax(g, "home").compareTo(new BigDecimal("666.67")));
    }

    /** 种子随真实订单叠加：主胜 2000+138，b_max 收缩。 */
    @Test
    void seedAddedOnTopOfConfirmedPayout() {
        FootballSportsOrder confirmed = order("O1", "主胜", 100, 1.38);

        List<MarketStakeAggregator.MarketGroupLimit> groups =
                MarketStakeAggregator.aggregate(List.of(confirmed), 0.2, 2000.0, null);
        MarketStakeAggregator.MarketGroupLimit g = groups.get(0);
        assertEquals(0, findStake(g, "home").compareTo(new BigDecimal("2138.00")));
        assertEquals(0, g.groupTotalStake.compareTo(new BigDecimal("6138.00")));
        // (0.4×6138−2138)/0.6 = 528.67
        assertEquals(0, findAcceptMax(g, "home").compareTo(new BigDecimal("528.67")));
    }

    private static FootballSportsOrder order(
            String orderId, String selection, long stakeYuan, double odds) {
        FootballSportsOrder o = new FootballSportsOrder();
        o.fixtureId = "13883500";
        o.orderId = orderId;
        o.playType = "胜平负";
        o.parlayType = "单关";
        o.handicapText = "无";
        o.selection = selection;
        o.odds = odds;
        o.stakeYuan = stakeYuan;
        return o;
    }

    private static BigDecimal findAcceptMax(MarketStakeAggregator.MarketGroupLimit g, String selection) {
        return row(g, selection).acceptMax;
    }

    private static BigDecimal findStake(MarketStakeAggregator.MarketGroupLimit g, String selection) {
        return row(g, selection).stake;
    }

    private static boolean findOver(MarketStakeAggregator.MarketGroupLimit g, String selection) {
        return row(g, selection).overLimit;
    }

    private static MarketStakeAggregator.OutcomeLimitRow row(
            MarketStakeAggregator.MarketGroupLimit g, String selection) {
        return g.rows.stream()
                .filter(r -> r.selection.equals(selection))
                .findFirst()
                .orElseThrow();
    }
}
