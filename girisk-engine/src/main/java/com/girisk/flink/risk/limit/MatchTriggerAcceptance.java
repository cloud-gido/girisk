package com.girisk.flink.risk.limit;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.MatchExposureAggregator.ExposureSummary;
import com.girisk.flink.risk.grid.ScoreGridSpec;
import com.girisk.flink.risk.model.EnrichedFootballOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * 单笔 trigger 的接单决策：<strong>先限额、后风险敞口</strong>（产品 v2 方案）。
 *
 * <ol>
 *   <li>Gate 1 等比例限额（恒开，返彩口径 + 冷启动种子）：本笔返彩 >= b_max → 拒，原因 LIMIT
 *   <li>Gate 2 风险敞口：试探窗口（prior + 本笔）最差净亏超阈值 → 拒，原因 EXPOSURE
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
        EXPOSURE
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
    /** Gate 1 命中：等比例限额拒单。 */
    public final boolean limitRejected;
    /** Gate 2 命中：风险敞口拒单。 */
    public final boolean exposureRejected;
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
        this.acceptedOrders = acceptedOrders;
        this.confirmedOrders = confirmedOrders;
        this.trialOrdersIncludingTrigger = trialOrdersIncludingTrigger;
        this.trialExposure = trialExposure;
        this.confirmedExposure = confirmedExposure;
        this.duplicateIgnored = duplicateIgnored;
        this.limitRejected = limitRejected;
        this.exposureRejected = exposureRejected;
        this.shouldReject = limitRejected || exposureRejected;
        this.triggerRejected = !duplicateIgnored && this.shouldReject;
        this.rejectReason =
                limitRejected
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
        List<FootballSportsOrder> trial = new ArrayList<>(priorConfirmed);
        if (!duplicateIgnored) {
            trial.add(trigger.order);
        }

        ExposureSummary confirmedExposure = MatchExposureAggregator.summarize(priorConfirmed, grid);
        ExposureSummary trialExposure = MatchExposureAggregator.summarize(trial, grid);

        List<FootballSportsOrder> priorForLimit =
                postFeedbackMode
                        ? priorConfirmed
                        : duplicateIgnored
                                ? priorConfirmed
                                : MarketStakeAggregator.excludeOrderId(trial, trigger.order.orderId);

        boolean limitRejected =
                !duplicateIgnored
                        && LimitRejectionPolicy.shouldReject(
                                trigger.order, priorForLimit, limitDelta, seedPayoutYuan);
        boolean exposureRejected =
                !duplicateIgnored
                        && !limitRejected
                        && ExposureLimitGate.exceedsWorstLoss(trialExposure, maxWorstLossYuan);

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
                limitRejected,
                exposureRejected,
                postFeedbackMode);
    }
}
