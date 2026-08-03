package com.girisk.sports.exposure;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiabilityCalculatorTest {

    @Test
    void bMax_payoutBasis_withSeed() {
        Map<String, BigDecimal> payouts = new LinkedHashMap<>();
        payouts.put("HOME", BigDecimal.ZERO);
        payouts.put("DRAW", BigDecimal.ZERO);
        payouts.put("AWAY", BigDecimal.ZERO);
        var result = LiabilityCalculator.calcBMaxWithSeed(
                "HOME", new String[]{"HOME", "DRAW", "AWAY"}, payouts, 0.2, new BigDecimal("2000"));
        // seed 2000 each → total 6000, w=1/3, factor=0.4 → bMax for equal = seed/3 ≈ 666.67
        assertEquals(new BigDecimal("666.67"), result.bMaxPayout());
    }

    @Test
    void mutualExclusionLiability_worstIsHome() {
        Map<String, BigDecimal> stakes = Map.of(
                "HOME", new BigDecimal("100"),
                "AWAY", new BigDecimal("50"));
        Map<String, BigDecimal> odds = Map.of(
                "HOME", new BigDecimal("5.0"),
                "AWAY", new BigDecimal("1.2"));
        var liab = LiabilityCalculator.calcMutualExclusionLiability(
                new String[]{"HOME", "AWAY"}, stakes, odds);
        // HOME wins: 100*(5-1) - 50 = 350; AWAY wins: 50*(1.2-1) - 100 = -40
        assertEquals("HOME", liab.worstSelection());
        assertEquals(35000L, liab.worstLiabilityCents());
    }

    @Test
    void payoutYuan() {
        assertEquals(new BigDecimal("2100.00"),
                LiabilityCalculator.payoutYuan(new BigDecimal("1000"), new BigDecimal("2.1")));
    }
}
