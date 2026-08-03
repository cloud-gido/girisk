package com.girisk.sports.exposure;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProportionalLimitCalculatorTest {

    @Test
    void oneXTwo_matchesProductDocument() {
        List<BigDecimal> amounts = List.of(
                new BigDecimal("10000"),
                new BigDecimal("3000"),
                new BigDecimal("2000"));
        List<ProportionalLimitCalculator.LimitResult> results =
                ProportionalLimitCalculator.calcAll(amounts, 0.2);

        assertEquals(new BigDecimal("5000.00"), results.get(0).targetAmount());
        assertEquals(new BigDecimal("6000.00"), results.get(0).maxAllowed());
        assertEquals(0, results.get(0).bMax().compareTo(BigDecimal.ZERO));

        assertEquals(new BigDecimal("5000.00"), results.get(1).targetAmount());
        assertEquals(new BigDecimal("6000.00"), results.get(1).maxAllowed());
        assertEquals(new BigDecimal("5000.00"), results.get(1).bMax());

        assertEquals(new BigDecimal("5000.00"), results.get(2).targetAmount());
        assertEquals(new BigDecimal("6000.00"), results.get(2).maxAllowed());
        assertEquals(new BigDecimal("6666.67"), results.get(2).bMax());
    }

    @Test
    void overUnder_matchesProductDocument() {
        List<BigDecimal> amounts = List.of(new BigDecimal("10000"), new BigDecimal("3000"));
        List<ProportionalLimitCalculator.LimitResult> results =
                ProportionalLimitCalculator.calcAll(amounts, 0.2);

        assertEquals(0, results.get(0).bMax().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("12000.00"), results.get(1).bMax());
    }
}
