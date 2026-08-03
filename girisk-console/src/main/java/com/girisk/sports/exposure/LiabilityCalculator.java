package com.girisk.sports.exposure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 真敞口 / 限额：组内按返彩 payout=stake×odds 聚合；等比例 b_max；
 * Gate2 按互斥结果的净赔付责任取最坏值。
 */
public final class LiabilityCalculator {

    private LiabilityCalculator() {}

    public record LimitResult(
            BigDecimal bMaxPayout,
            BigDecimal targetPayout,
            BigDecimal maxAllowedPayout,
            BigDecimal currentPayout,
            BigDecimal totalPayout,
            boolean overLimit
    ) {}

    public record LiabilityResult(
            Map<String, Long> liabilityBySelectionCents,
            long worstLiabilityCents,
            String worstSelection
    ) {}

    /**
     * b_max = ((1+δ)·w·S_total − S_i) / (1 − (1+δ)·w)，S 为返彩口径。
     */
    public static LimitResult calcBMax(
            BigDecimal currentPayout,
            List<BigDecimal> groupPayouts,
            double delta) {
        int n = groupPayouts.size();
        if (n < 2) throw new IllegalArgumentException("group must have at least 2 outcomes");

        BigDecimal total = groupPayouts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal weight = BigDecimal.valueOf(1.0 / n);
        BigDecimal onePlusDelta = BigDecimal.valueOf(1 + delta);
        BigDecimal factor = onePlusDelta.multiply(weight);
        BigDecimal denominator = BigDecimal.ONE.subtract(factor);

        BigDecimal target = total.multiply(weight).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxAllowed = total.multiply(factor).setScale(2, RoundingMode.HALF_UP);

        BigDecimal numerator = factor.multiply(total).subtract(currentPayout);
        BigDecimal bMax = denominator.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : numerator.divide(denominator, 2, RoundingMode.HALF_UP);
        if (bMax.compareTo(BigDecimal.ZERO) < 0) {
            bMax = BigDecimal.ZERO;
        }
        boolean over = currentPayout.compareTo(maxAllowed) > 0;
        return new LimitResult(bMax, target, maxAllowed, currentPayout, total, over);
    }

    /** 组内每个盘口叠加虚拟种子（返彩口径）后计算 b_max。 */
    public static LimitResult calcBMaxWithSeed(
            String selection,
            String[] selections,
            Map<String, BigDecimal> groupPayouts,
            double delta,
            BigDecimal seedPayoutPerSelection) {
        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal current = BigDecimal.ZERO;
        for (String sel : selections) {
            BigDecimal p = groupPayouts.getOrDefault(sel, BigDecimal.ZERO).add(seedPayoutPerSelection);
            amounts.add(p);
            if (sel.equals(selection)) {
                current = p;
            }
        }
        return calcBMax(current, amounts, delta);
    }

    /**
     * 对互斥盘口：若结果 o 发生，平台净责任 =
     * Σ stake×(odds-1) for bets on o − Σ stake for bets not on o。
     * 取最坏（最大正责任）作为敞口。
     */
    public static LiabilityResult calcMutualExclusionLiability(
            String[] selections,
            Map<String, BigDecimal> stakesYuan,
            Map<String, BigDecimal> oddsBySelection) {
        Map<String, Long> liability = new LinkedHashMap<>();
        long worst = Long.MIN_VALUE;
        String worstSel = selections[0];
        for (String outcome : selections) {
            BigDecimal net = BigDecimal.ZERO;
            for (String sel : selections) {
                BigDecimal stake = stakesYuan.getOrDefault(sel, BigDecimal.ZERO);
                if (stake.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal odds = oddsBySelection.getOrDefault(sel, BigDecimal.ONE);
                if (sel.equals(outcome)) {
                    net = net.add(stake.multiply(odds.subtract(BigDecimal.ONE)));
                } else {
                    net = net.subtract(stake);
                }
            }
            long cents = net.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
            liability.put(outcome, cents);
            if (cents > worst) {
                worst = cents;
                worstSel = outcome;
            }
        }
        if (worst == Long.MIN_VALUE) {
            worst = 0;
        }
        return new LiabilityResult(liability, Math.max(0, worst), worstSel);
    }

    public static BigDecimal payoutYuan(BigDecimal stakeYuan, BigDecimal odds) {
        BigDecimal o = odds != null && odds.compareTo(BigDecimal.ZERO) > 0 ? odds : BigDecimal.ONE;
        return stakeYuan.multiply(o).setScale(2, RoundingMode.HALF_UP);
    }

    public static long toCents(BigDecimal yuan) {
        return yuan.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
