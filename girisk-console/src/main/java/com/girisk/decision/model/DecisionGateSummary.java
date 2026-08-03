package com.girisk.decision.model;

/**
 * 值班可读的两道闸门摘要（非完整 productAudit）。
 */
public record DecisionGateSummary(
        String rejectReason,
        boolean limitRejected,
        boolean exposureRejected,
        Gate1 gate1,
        Gate2 gate2) {

    public record Gate1(
            String selectionLabel,
            String groupKey,
            Double proposedPayoutYuan,
            Double stakeBeforeYuan,
            Double targetAmountYuan,
            Double maxAllowedYuan,
            Double acceptMaxYuan,
            Double limitDelta,
            Double seedPayoutYuan,
            Boolean overLimitBefore) {}

    public record Gate2(
            Double trialWorstLossYuan,
            Double maxWorstLossYuan,
            String worstScore,
            Double beforeWorstPnlYuan,
            Double trialWorstPnlYuan,
            Double afterWorstPnlYuan,
            Boolean exceeded) {}
}
