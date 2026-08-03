package com.girisk.sports.config;

import com.girisk.sports.model.MarketGroupKey;
import com.girisk.sports.model.SportsMarketType;
import com.girisk.sports.service.SportsExposureService;
import com.girisk.sports.store.ExposureStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/** Demo stake seeder — only when girisk.demo-data.enabled=true (profile demo). */
@Component
@ConditionalOnProperty(name = "girisk.demo-data.enabled", havingValue = "true")
public class SportsDemoDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(SportsDemoDataInitializer.class);

    private final ExposureStore exposureStore;
    private final SportsExposureService exposureService;

    public SportsDemoDataInitializer(ExposureStore exposureStore, SportsExposureService exposureService) {
        this.exposureStore = exposureStore;
        this.exposureService = exposureService;
    }

    @PostConstruct
    public void seed() {
        seedIfEmpty(MarketGroupKey.of("MATCH-001", SportsMarketType.ONE_X_TWO, ""), Map.of(
                "home", new BigDecimal("10000"),
                "draw", new BigDecimal("3000"),
                "away", new BigDecimal("2000")));
        seedIfEmpty(MarketGroupKey.of("MATCH-001", SportsMarketType.OVER_UNDER, "3"), Map.of(
                "over", new BigDecimal("10000"),
                "under", new BigDecimal("3000")));
        seedIfEmpty(MarketGroupKey.of("MATCH-001", SportsMarketType.HANDICAP, "1"), Map.of(
                "home", new BigDecimal("10000"),
                "away", new BigDecimal("3000")));
        seedIfEmpty(MarketGroupKey.of("MATCH-002", SportsMarketType.ONE_X_TWO, ""), Map.of(
                "home", new BigDecimal("1030"),
                "draw", new BigDecimal("160"),
                "away", new BigDecimal("510.12")));
        exposureService.runExposureCheck("MATCH-001");
        exposureService.runExposureCheck("MATCH-002");
        log.info("Sports demo stakes initialized for MATCH-001 / MATCH-002");
    }

    private void seedIfEmpty(MarketGroupKey key, Map<String, BigDecimal> stakes) {
        String firstKey = stakes.keySet().iterator().next();
        if (exposureStore.getStake(key, firstKey).compareTo(BigDecimal.ZERO) > 0) {
            return;
        }
        exposureStore.seedGroup(key, stakes);
    }
}
