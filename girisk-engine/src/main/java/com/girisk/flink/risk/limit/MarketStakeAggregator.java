package com.girisk.flink.risk.limit;

import com.girisk.flink.risk.excel.FootballSportsOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从场次窗口订单累计各盘口<strong>返彩金额（投注金额 × 赔率）</strong>，并计算等比例限额。
 *
 * <p>冷启动：分组内每个盘口先叠加虚拟种子 {@code seedYuan}（与产品计算器「初始已投注金额」一致），
 * 避免 {@code S_total=0 → b_max=0} 时首单必拒。
 */
public final class MarketStakeAggregator {

    public static final class OutcomeLimitRow {
        public final String selection;
        public final String selectionLabel;
        public final BigDecimal stake;
        public final BigDecimal targetAmount;
        public final BigDecimal maxAllowedAmount;
        public final BigDecimal acceptMax;
        public final boolean overLimit;

        public OutcomeLimitRow(
                String selection,
                String selectionLabel,
                BigDecimal stake,
                BigDecimal targetAmount,
                BigDecimal maxAllowedAmount,
                BigDecimal acceptMax,
                boolean overLimit) {
            this.selection = selection;
            this.selectionLabel = selectionLabel;
            this.stake = stake;
            this.targetAmount = targetAmount;
            this.maxAllowedAmount = maxAllowedAmount;
            this.acceptMax = acceptMax;
            this.overLimit = overLimit;
        }
    }

    public static final class MarketGroupLimit {
        public final LimitMarketType marketType;
        public final String marketTypeCode;
        public final String marketLabel;
        public final String line;
        public final String groupKey;
        public final Map<String, BigDecimal> stakes;
        public final List<OutcomeLimitRow> rows;
        public final BigDecimal groupTotalStake;

        public MarketGroupLimit(
                LimitMarketType marketType,
                String marketTypeCode,
                String marketLabel,
                String line,
                String groupKey,
                Map<String, BigDecimal> stakes,
                List<OutcomeLimitRow> rows,
                BigDecimal groupTotalStake) {
            this.marketType = marketType;
            this.marketTypeCode = marketTypeCode;
            this.marketLabel = marketLabel;
            this.line = line;
            this.groupKey = groupKey;
            this.stakes = stakes;
            this.rows = rows;
            this.groupTotalStake = groupTotalStake;
        }
    }

    private MarketStakeAggregator() {}

    /** 订单占用限额的返彩金额 = 投注金额 × 赔率（元，2 位小数；支持分位本金）。 */
    public static BigDecimal payoutYuan(FootballSportsOrder order) {
        return BigDecimal.valueOf(order.stakeCents())
                .multiply(BigDecimal.valueOf(order.odds))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** 排除指定 orderId（首条匹配）后的窗口订单，用于 trigger 前限额。 */
    public static List<FootballSportsOrder> excludeOrderId(
            List<FootballSportsOrder> orders, String orderId) {
        String key = normalizeOrderId(orderId);
        if (key.isEmpty()) {
            return orders;
        }
        List<FootballSportsOrder> out = new ArrayList<>(orders.size());
        boolean removed = false;
        for (FootballSportsOrder order : orders) {
            if (!removed && key.equals(normalizeOrderId(order.orderId))) {
                removed = true;
                continue;
            }
            out.add(order);
        }
        return out;
    }

    /** 无种子聚合（种子=0，不确保额外分组）。 */
    public static List<MarketGroupLimit> aggregate(List<FootballSportsOrder> orders, double delta) {
        return aggregate(orders, delta, 0.0, null);
    }

    /**
     * 返彩口径聚合。
     *
     * @param seedYuan 每盘口虚拟种子（返彩口径）
     * @param ensureGroupFor 非空时确保该订单所属分组存在（即使窗口内无该组订单，冷启动首单可算 b_max）
     */
    public static List<MarketGroupLimit> aggregate(
            List<FootballSportsOrder> orders,
            double delta,
            double seedYuan,
            FootballSportsOrder ensureGroupFor) {
        Map<String, MutableGroup> groups = new LinkedHashMap<>();
        for (FootballSportsOrder order : orders) {
            Optional<LimitSelectionResolver.ResolvedOutcome> resolved =
                    LimitSelectionResolver.resolve(order);
            if (!resolved.isPresent()) {
                continue;
            }
            LimitSelectionResolver.ResolvedOutcome r = resolved.get();
            String gk = LimitSelectionResolver.groupKey(r.marketType, r.line);
            MutableGroup g =
                    groups.computeIfAbsent(
                            gk, k -> new MutableGroup(r.marketType, r.line));
            g.stakes.merge(r.selectionKey, payoutYuan(order), BigDecimal::add);
        }
        if (ensureGroupFor != null) {
            Optional<LimitSelectionResolver.ResolvedOutcome> resolved =
                    LimitSelectionResolver.resolve(ensureGroupFor);
            if (resolved.isPresent()) {
                LimitSelectionResolver.ResolvedOutcome r = resolved.get();
                String gk = LimitSelectionResolver.groupKey(r.marketType, r.line);
                groups.computeIfAbsent(gk, k -> new MutableGroup(r.marketType, r.line));
            }
        }

        BigDecimal seed = BigDecimal.valueOf(Math.max(0.0, seedYuan)).setScale(2, RoundingMode.HALF_UP);
        List<MarketGroupLimit> result = new ArrayList<>();
        for (MutableGroup g : groups.values()) {
            result.add(g.toLimit(delta, seed));
        }
        return result;
    }

    private static final class MutableGroup {
        final LimitMarketType marketType;
        final String line;
        final Map<String, BigDecimal> stakes = new LinkedHashMap<>();

        MutableGroup(LimitMarketType marketType, String line) {
            this.marketType = marketType;
            this.line = line == null ? "" : line;
        }

        MarketGroupLimit toLimit(double delta, BigDecimal seed) {
            String[] selections = marketType.selections();
            List<BigDecimal> amounts = new ArrayList<>();
            for (String sel : selections) {
                amounts.add(stakes.getOrDefault(sel, BigDecimal.ZERO).add(seed));
            }
            List<ProportionalLimitCalculator.LimitResult> limits =
                    ProportionalLimitCalculator.calcAll(amounts, delta);

            List<OutcomeLimitRow> rows = new ArrayList<>();
            for (int i = 0; i < selections.length; i++) {
                ProportionalLimitCalculator.LimitResult lr = limits.get(i);
                rows.add(
                        new OutcomeLimitRow(
                                selections[i],
                                selectionLabel(marketType, line, selections[i]),
                                lr.currentAmount,
                                lr.targetAmount,
                                lr.maxAllowed,
                                lr.bMax,
                                lr.overLimit));
            }

            BigDecimal total = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, BigDecimal> stakeMap = new LinkedHashMap<>();
            for (int i = 0; i < selections.length; i++) {
                stakeMap.put(selections[i], amounts.get(i));
            }

            return new MarketGroupLimit(
                    marketType,
                    marketType.name(),
                    marketType.label(),
                    line,
                    LimitSelectionResolver.groupKey(marketType, line),
                    stakeMap,
                    rows,
                    total);
        }
    }

    private static String normalizeOrderId(String orderId) {
        return orderId == null ? "" : orderId.trim();
    }

    private static String selectionLabel(LimitMarketType type, String line, String key) {
        if (type == LimitMarketType.ONE_X_TWO || type == LimitMarketType.HANDICAP_THREE_WAY) {
            if ("home".equals(key)) {
                return "主胜";
            }
            if ("draw".equals(key)) {
                return "平局";
            }
            if ("away".equals(key)) {
                return "客胜";
            }
            return key;
        }
        if (type == LimitMarketType.OVER_UNDER) {
            return overUnderLineLabel(line, key);
        }
        if (type == LimitMarketType.HANDICAP) {
            return handicapSignedLineLabel(line, key);
        }
        return key;
    }

    /** 大小球：Over / Under + line（业界惯例）。 */
    static String overUnderLineLabel(String line, String key) {
        String suffix = line == null || line.isBlank() ? "" : " " + line.trim();
        if ("over".equals(key)) {
            return "Over" + suffix;
        }
        if ("under".equals(key)) {
            return "Under" + suffix;
        }
        return key;
    }

    /** 亚洲让球：home=+line，away=-line（主队视角，如主-1:客+1）。 */
    static String handicapSignedLineLabel(String line, String key) {
        double homeLine = parseLineValue(line);
        if ("home".equals(key)) {
            return formatSignedLine(homeLine);
        }
        if ("away".equals(key)) {
            return formatSignedLine(-homeLine);
        }
        return key;
    }

    private static double parseLineValue(String line) {
        if (line == null || line.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(line.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    static String formatSignedLine(double value) {
        if (value == Math.rint(value)) {
            long i = (long) value;
            return i > 0 ? "+" + i : String.valueOf(i);
        }
        String formatted = String.format(java.util.Locale.ROOT, "%.2f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
        return value > 0 ? "+" + formatted : formatted;
    }
}
