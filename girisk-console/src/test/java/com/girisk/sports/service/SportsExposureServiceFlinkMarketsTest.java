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
    void mapFlinkMarketGroups_emptyWhenMissing() {
        assertTrue(SportsExposureService.mapFlinkMarketGroups(null).isEmpty());
        assertTrue(SportsExposureService.mapFlinkMarketGroups(List.of()).isEmpty());
    }
}
