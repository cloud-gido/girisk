package com.girisk.flink.risk.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeRiskConfigResolverTest {

    @Test
    void matchOverridesLeague() {
        ScopeRiskConfigResolver resolver = new ScopeRiskConfigResolver(0.2, 2000, 1000);
        Map<String, ScopeRiskConfigLayer> layers = new HashMap<>();
        ScopeRiskConfigLayer league = new ScopeRiskConfigLayer();
        league.scopeType = "LEAGUE";
        league.scopeKey = "football:L1";
        league.limitGateEnabled = false;
        league.delta = 0.3;
        layers.put(league.mapKey(), league);
        ScopeRiskConfigLayer match = new ScopeRiskConfigLayer();
        match.scopeType = "MATCH";
        match.scopeKey = "M1";
        match.limitGateEnabled = true;
        match.exposureGateEnabled = false;
        layers.put(match.mapKey(), match);

        EffectiveScopeRiskParams p = resolver.resolve(layers, "M1", "football", "L1");
        assertTrue(p.limitGateEnabled);
        assertFalse(p.exposureGateEnabled);
        assertEquals(0.3, p.limitDelta, 1e-9);
        assertEquals("MATCH", p.limitGateSource);
        assertEquals("MATCH", p.exposureGateSource);
        assertEquals("LEAGUE", p.limitsSource);
    }

    @Test
    void defaultsWhenEmpty() {
        ScopeRiskConfigResolver resolver = new ScopeRiskConfigResolver(0.2, 2000, 1000);
        EffectiveScopeRiskParams p = resolver.resolve(Map.of(), "M1", "football", "L1");
        assertTrue(p.tradingEnabled);
        assertEquals(0.2, p.limitDelta, 1e-9);
        assertEquals(0.0, p.maxBetPayoutYuan, 1e-9);
        assertEquals("CLI", p.limitsSource);
    }

    @Test
    void matchMaxBetOverridesLeague() {
        ScopeRiskConfigResolver resolver = new ScopeRiskConfigResolver(0.2, 2000, 1000, 500);
        Map<String, ScopeRiskConfigLayer> layers = new HashMap<>();
        ScopeRiskConfigLayer league = new ScopeRiskConfigLayer();
        league.scopeType = "LEAGUE";
        league.scopeKey = "football:L1";
        league.maxBetPayoutYuan = 800.0;
        layers.put(league.mapKey(), league);
        ScopeRiskConfigLayer match = new ScopeRiskConfigLayer();
        match.scopeType = "MATCH";
        match.scopeKey = "M1";
        match.maxBetPayoutYuan = 300.0;
        layers.put(match.mapKey(), match);

        EffectiveScopeRiskParams p = resolver.resolve(layers, "M1", "football", "L1");
        assertEquals(300.0, p.maxBetPayoutYuan, 1e-9);
        assertEquals("MATCH", p.limitsSource);
    }
}
