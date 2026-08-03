package com.girisk.sports.exposure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 互斥盘口等比例限额：b_max = ((1+δ)·w·S_total - S_i) / (1 - (1+δ)·w)
 */
public final class ProportionalLimitCalculator {

    private ProportionalLimitCalculator() {}

    public record LimitResult(
            BigDecimal bMax,
            BigDecimal targetAmount,
            BigDecimal maxAllowed,
            BigDecimal currentAmount,
            BigDecimal totalAmount,
            boolean overLimit
    ) {}

    public static LimitResult calc(BigDecimal current, List<BigDecimal> groupAmounts, double delta) {
        int n = groupAmounts.size();
        if (n < 2) throw new IllegalArgumentException("group must have at least 2 outcomes");

        BigDecimal total = groupAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal weight = BigDecimal.valueOf(1.0 / n);
        BigDecimal onePlusDelta = BigDecimal.valueOf(1 + delta);
        BigDecimal factor = onePlusDelta.multiply(weight);
        BigDecimal denominator = BigDecimal.ONE.subtract(factor);

        BigDecimal target = total.multiply(weight).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxAllowed = total.multiply(factor).setScale(2, RoundingMode.HALF_UP);

        BigDecimal numerator = factor.multiply(total).subtract(current);
        BigDecimal bMax = denominator.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : numerator.divide(denominator, 2, RoundingMode.HALF_UP);

        if (bMax.compareTo(BigDecimal.ZERO) < 0) {
            bMax = BigDecimal.ZERO;
        }

        boolean over = current.compareTo(maxAllowed) > 0;
        return new LimitResult(bMax, target, maxAllowed, current, total, over);
    }

    public static List<LimitResult> calcAll(List<BigDecimal> groupAmounts, double delta) {
        List<LimitResult> results = new ArrayList<>();
        for (BigDecimal amount : groupAmounts) {
            results.add(calc(amount, groupAmounts, delta));
        }
        return results;
    }
}
