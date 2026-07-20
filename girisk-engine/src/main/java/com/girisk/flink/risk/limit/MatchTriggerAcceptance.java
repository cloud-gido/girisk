package com.girisk.flink.risk.limit;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.MatchExposureAggregator.ExposureSummary;
import com.girisk.flink.risk.grid.ScoreGridSpec;
import com.girisk.flink.risk.model.EnrichedFootballOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * 单笔 trigger 的接单决策：<strong>Gate0 单注上限 → Gate1 等比例限额 → Gate2 风险敞口</strong>。
 *
 * <ol>
 *   <li>Gate 0 单注返彩上限（可选）：本笔返彩 &gt; maxBet → 拒，原因 LIMIT（maxBetRejected）
 *   <li>Gate 1 等比例限额（返彩口径 + 冷启动种子）：本笔返彩 &gt;= b_max → 拒，原因 LIMIT
 *   <li>Gate 2 风险敞口：试探窗口最差净亏超阈值 → 拒，原因 EXPOSURE
 * </ol>
 *
 * <p>post 回传模式（{@code postFeedbackMode=true}）：prior 窗口仅来自
 * {@code girisk.trading.order.risk-check.post.v1} 的 CONFIRMED。
 */
public final class MatchTriggerAcceptance {

    /** 拒单原因（建议值，业务侧可 override）。 */
    public enum RejectReason {
        NONE,
        LIMIT,
        EXPOSURE,
        /** 总开关关闭（停盘） */
        TRADING
    }

    /** Summary 敞口窗口（post 模式下仅 CONFIRMED）。 */
    public final List<FootballSportsOrder> acceptedOrders;
    /** post topic 已 CONFIRMED 订单（Limit 基数）。 */
    public final List<FootballSportsOrder> confirmedOrders;
    /** 含 trigger 的试探订单集（duplicate 时 trigger 已在 prior 中）。 */
    public final List<FootballSportsOrder> trialOrdersIncludingTrigger;
    public final ExposureSummary trialExposure;
    public final ExposureSummary confirmedExposure;
    public final boolean duplicateIgnored;
    public final boolean triggerRejected;
    /** Gate 0 命中：单注返彩上限。 */
    public final boolean maxBetRejected;
    /** Gate 1 命中：等比例限额拒单（含 Gate0）。 */
    public final boolean limitRejected;
    /** Gate 2 命中：风险敞口拒单。 */
    public final boolean exposureRejected;
    /** 总开关关闭。 */
    public final boolean tradingRejected;
    public final boolean shouldReject;
    public final RejectReason rejectReason;
    public final boolean postFeedbackMode;

    public MatchTriggerAcceptance(
            List<FootballSportsOrder> acceptedOrders,
            List<FootballSportsOrder> confirmedOrders,
            List<FootballSportsOrder> trialOrdersIncludingTrigger,
            ExposureSummary trialExposure,
            ExposureSummary confirmedExposure,
            boolean duplicateIgnored,
            boolean limitRejected,
            boolean exposureRejected,
            boolean postFeedbackMode) {
        this(
                acceptedOrders,
                confirmedOrders,
                trialOrdersIncludingTrigger,
                trialExposure,
                confirmedExposure,
                duplicateIgnored,
                false,
                limitRejected,
                exposureRejected,
                false,
                postFeedbackMode);
    }

    public MatchTriggerAcceptance(
            List<FootballSportsOrder> acceptedOrders,
            List<FootballSportsOrder> confirmedOrders,
            List<FootballSportsOrder> trialOrdersIncludingTrigger,
            ExposureSummary trialExposure,
            ExposureSummary confirmedExposure,
            boolean duplicateIgnored,
            boolean limitRejected,
            boolean exposureRejected,
            boolean tradingRejected,
            boolean postFeedbackMode) {
        this(
                acceptedOrders,
                confirmedOrders,
                trialOrdersIncludingTrigger,
                trialExposure,
                confirmedExposure,
                duplicateIgnored,
                false,
                limitRejected,
                exposureRejected,
                tradingRejected,
                postFeedbackMode);
    }

    public MatchTriggerAcceptance(
            List<FootballSportsOrder> acceptedOrders,
            List<FootballSportsOrder> confirmedOrders,
            List<FootballSportsOrder> trialOrdersIncludingTrigger,
            ExposureSummary trialExposure,
            ExposureSummary confirmedExposure,
            boolean duplicateIgnored,
            boolean maxBetRejected,
            boolean limitRejected,
            boolean exposureRejected,
            boolean tradingRejected,
            boolean postFeedbackMode) {
        this.acceptedOrders = acceptedOrders;
        this.confirmedOrders = confirmedOrders;
        this.trialOrdersIncludingTrigger = trialOrdersIncludingTrigger;
        this.trialExposure = trialExposure;
        this.confirmedExposure = confirmedExposure;
        this.duplicateIgnored = duplicateIgnored;
        this.maxBetRejected = maxBetRejected;
        this.limitRejected = limitRejected;
        this.exposureRejected = exposureRejected;
        this.tradingRejected = tradingRejected;
        this.shouldReject = tradingRejected || limitRejected || exposureRejected;
        this.triggerRejected = !duplicateIgnored && this.shouldReject;
        this.rejectReason =
                tradingRejected
                        ? RejectReason.TRADING
                        : limitRejected
                                ? RejectReason.LIMIT
                                : exposureRejected ? RejectReason.EXPOSURE : RejectReason.NONE;
        this.postFeedbackMode = postFeedbackMode;
    }

    public boolean persistTrigger() {
        return !duplicateIgnored && !triggerRejected;
    }

    public static MatchTriggerAcceptance evaluate(
            List<FootballSportsOrder> priorConfirmed,
            EnrichedFootballOrder trigger,
            boolean duplicateIgnored,
            ScoreGridSpec grid,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            boolean postFeedbackMode) {
        return evaluate(
                priorConfirmed,
                trigger,
                duplicateIgnored,
                grid,
                limitDelta,
                seedPayoutYuan,
                maxWorstLossYuan,
                0.0,
                true,
                true,
                true,
                postFeedbackMode);
    }

    public static MatchTriggerAcceptance evaluate(
            List<FootballSportsOrder> priorConfirmed,
            EnrichedFootballOrder trigger,
            boolean duplicateIgnored,
            ScoreGridSpec grid,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            boolean tradingEnabled,
            boolean limitGateEnabled,
            boolean exposureGateEnabled,
            boolean postFeedbackMode) {
        return evaluate(
                priorConfirmed,
                trigger,
                duplicateIgnored,
                grid,
                limitDelta,
                seedPayoutYuan,
                maxWorstLossYuan,
                0.0,
                tradingEnabled,
                limitGateEnabled,
                exposureGateEnabled,
                postFeedbackMode);
    }

    public static MatchTriggerAcceptance evaluate(
            List<FootballSportsOrder> priorConfirmed,
            EnrichedFootballOrder trigger,
            boolean duplicateIgnored,
            ScoreGridSpec grid,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            double maxBetPayoutYuan,
            boolean tradingEnabled,
            boolean limitGateEnabled,
            boolean exposureGateEnabled,
            boolean postFeedbackMode) {
        List<FootballSportsOrder> trial = new ArrayList<>(priorConfirmed);
        if (!duplicateIgnored) {
            trial.add(trigger.order);
        }

        ExposureSummary confirmedExposure = MatchExposureAggregator.summarize(priorConfirmed, grid);
        ExposureSummary trialExposure = MatchExposureAggregator.summarize(trial, grid);

        if (!duplicateIgnored && !tradingEnabled) {
            return new MatchTriggerAcceptance(
                    List.copyOf(priorConfirmed),
                    List.copyOf(priorConfirmed),
                    trial,
                    trialExposure,
                    confirmedExposure,
                    duplicateIgnored,
                    false,
                    false,
                    false,
                    true,
                    postFeedbackMode);
        }

        List<FootballSportsOrder> priorForLimit =
                postFeedbackMode
                        ? priorConfirmed
                        : duplicateIgnored
                                ? priorConfirmed
                                : MarketStakeAggregator.excludeOrderId(trial, trigger.order.orderId);

        boolean maxBetRejected =
                limitGateEnabled
                        && !duplicateIgnored
                        && LimitRejectionPolicy.shouldRejectMaxBet(trigger.order, maxBetPayoutYuan);
        boolean proportionalRejected =
                limitGateEnabled
                        && !duplicateIgnored
                        && !maxBetRejected
                        && LimitRejectionPolicy.shouldReject(
                                trigger.order, priorForLimit, limitDelta, seedPayoutYuan);
        boolean limitRejected = maxBetRejected || proportionalRejected;
        double exposureThreshold =
                exposureGateEnabled ? maxWorstLossYuan : ExposureLimitGate.WORST_LOSS_DISABLED;
        boolean exposureRejected =
                !duplicateIgnored
                        && !limitRejected
                        && ExposureLimitGate.exceedsWorstLoss(trialExposure, exposureThreshold);

        boolean triggerRejected = !duplicateIgnored && (limitRejected || exposureRejected);
        List<FootballSportsOrder> acceptedOrders;
        if (postFeedbackMode || duplicateIgnored || triggerRejected) {
            acceptedOrders = List.copyOf(priorConfirmed);
        } else {
            acceptedOrders = List.copyOf(trial);
        }

        return new MatchTriggerAcceptance(
                acceptedOrders,
                List.copyOf(priorConfirmed),
                trial,
                trialExposure,
                confirmedExposure,
                duplicateIgnored,
                maxBetRejected,
                limitRejected,
                exposureRejected,
                false,
                postFeedbackMode);
    }
}
