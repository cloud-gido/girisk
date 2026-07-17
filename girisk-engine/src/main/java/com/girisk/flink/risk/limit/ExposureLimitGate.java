package com.girisk.flink.risk.limit;

import com.girisk.flink.risk.grid.MatchExposureAggregator.ExposureSummary;
import com.girisk.flink.risk.kafka.MatchExposureSummaryJson;

/** Gate 2 风险敞口：试探窗口最差净亏超过阈值即拒单。 */
public final class ExposureLimitGate {

    /** 传入该值表示关闭敞口闸门（仅限额生效）。 */
    public static final double WORST_LOSS_DISABLED = Double.MAX_VALUE;

    private ExposureLimitGate() {}

    /**
     * 与 Summary {@code maxProfitYuan} 同源：网格中平台净支出最大值（分）。
     *
     * <p>等价于存在亏损格时的 {@code maxBookmakerLossCents}。
     */
    public static long maxExposureCents(ExposureSummary exposure) {
        if (exposure == null || exposure.scenarios.isEmpty()) {
            return 0L;
        }
        MatchExposureSummaryJson.MaxProfitMeta meta =
                MatchExposureSummaryJson.maxProfitMeta(exposure.scenarios);
        return meta == null ? 0L : meta.maxProfitCents;
    }

    public static double maxExposureYuan(ExposureSummary exposure) {
        return maxExposureCents(exposure) / 100.0;
    }

    /**
     * {@code true} = 最差净盈亏低于阈值线，应拒单。
     *
     * <p>阈值兼容产品口径：传 {@code 1000} 或 {@code -1000} 均表示「最差净盈亏 < -1000 拒」；
     * 严格小于（临界值不拒），与产品计算器一致。
     */
    public static boolean exceedsWorstLoss(ExposureSummary exposure, double maxWorstLossYuan) {
        if (maxWorstLossYuan >= WORST_LOSS_DISABLED) {
            return false;
        }
        long thresholdCents = Math.round(Math.abs(maxWorstLossYuan) * 100.0);
        return maxExposureCents(exposure) > thresholdCents;
    }
}
