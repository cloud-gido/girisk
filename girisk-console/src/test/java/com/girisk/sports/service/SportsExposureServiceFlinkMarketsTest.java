package com.girisk.sports.service;

import com.girisk.sports.dto.SportsMatchView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SportsExposureServiceFlinkMarketsTest {

    @Test
    void mapFlinkMarketGroups_mapsPayoutOutcomes() {
        Map<String, Object> home = new LinkedHashMap<>();
        home.put("selection", "home");
        home.put("stake", 210);
        home.put("targetAmount", 5000);
        home.put("maxAllowedAmount", 6000);
        home.put("acceptMax", 1666.67);

        Map<String, Object> draw = new LinkedHashMap<>();
        draw.put("selection", "draw");
        draw.put("stake", 0);
        draw.put("targetAmount", 5000);
        draw.put("maxAllowedAmount", 6000);
        draw.put("acceptMax", 2000);

        Map<String, Object> away = new LinkedHashMap<>();
        away.put("selection", "away");
        away.put("stake", 0);
        away.put("targetAmount", 5000);
        away.put("maxAllowedAmount", 6000);
        away.put("acceptMax", 2000);

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("marketType", "ONE_X_TWO");
        group.put("marketLabel", "胜负平");
        group.put("line", "");
        group.put("outcomes", List.of(home, draw, away));

        List<SportsMatchView.MarketGroupView> mapped =
                SportsExposureService.mapFlinkMarketGroups(List.of(group));

        assertEquals(1, mapped.size());
        assertEquals("ONE_X_TWO", mapped.get(0).marketType());
        assertEquals(0, new BigDecimal("210").compareTo(mapped.get(0).stakes().get("home")));
        assertEquals(0, new BigDecimal("1666.67").compareTo(mapped.get(0).limits().get("home")));
        assertEquals(3, mapped.get(0).outcomes().size());
    }

    @Test
    void sumGroupStakes_prefersActualStakeExcludingSeed() {
        Map<String, Object> home = new LinkedHashMap<>();
        home.put("selection", "home");
        home.put("stake", 6000); // book = actual + seed
        home.put("actualStake", 0);
        home.put("seedYuan", 2000);
        home.put("targetAmount", 2000);
        home.put("maxAllowedAmount", 2400);
        home.put("acceptMax", 400);

        Map<String, Object> draw = new LinkedHashMap<>();
        draw.put("selection", "draw");
        draw.put("stake", 2000);
        draw.put("actualStake", 0);
        draw.put("seedYuan", 2000);
        draw.put("targetAmount", 2000);
        draw.put("maxAllowedAmount", 2400);
        draw.put("acceptMax", 400);

        Map<String, Object> away = new LinkedHashMap<>();
        away.put("selection", "away");
        away.put("stake", 2500);
        away.put("actualStake", 500);
        away.put("seedYuan", 2000);
        away.put("targetAmount", 2000);
        away.put("maxAllowedAmount", 2400);
        away.put("acceptMax", 100);

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("marketType", "ONE_X_TWO");
        group.put("marketLabel", "胜负平");
        group.put("line", "");
        group.put("outcomes", List.of(home, draw, away));

        List<SportsMatchView.MarketGroupView> mapped =
                SportsExposureService.mapFlinkMarketGroups(List.of(group));

        // 冷启动账面合计 10500，真实已投注仅 500
        assertEquals(0, new BigDecimal("500").compareTo(SportsExposureService.sumGroupStakes(mapped)));
    }

    @Test
    void mapFlinkMarketGroups_emptyWhenMissing() {
        assertTrue(SportsExposureService.mapFlinkMarketGroups(null).isEmpty());
        assertTrue(SportsExposureService.mapFlinkMarketGroups(List.of()).isEmpty());
    }
}
