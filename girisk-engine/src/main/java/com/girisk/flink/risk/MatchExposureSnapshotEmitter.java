package com.girisk.flink.risk;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.MatchExposureAggregator.ExposureSummary;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.kafka.MatchExposureSummaryJson;
import com.girisk.flink.risk.kafka.MatchLimitSummaryJson;
import com.girisk.flink.risk.kafka.MatchRiskBusinessSnapshotJson;
import com.girisk.flink.risk.kafka.RiskDecisionJson;
import com.girisk.flink.risk.limit.ExposureLimitGate;
import com.girisk.flink.risk.limit.MatchTriggerAcceptance;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.util.List;

/** Summary + Limit 侧输出：Summary 仅含已接单；Limit 试探敞口含 trigger。 */
final class MatchExposureSnapshotEmitter {

    private MatchExposureSnapshotEmitter() {}

    static void emit(
            EnrichedFootballOrder trigger,
            String matchKey,
            MatchTriggerAcceptance acceptance,
            ScoreGridParams gridParams,
            boolean eventTimeOutOfOrder,
            long publishedAtMs,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            boolean emitSummarySnapshot,
            boolean emitLimitSnapshot,
            boolean emitBusinessSnapshot,
            boolean emitDecision,
            Collector<String> mainOut,
            OutputTag<String> limitTag,
            OutputTag<String> businessTag,
            OutputTag<String> decisionTag,
            SideOutputContext sideOutput) {
        emit(
                trigger,
                matchKey,
                acceptance,
                List.of(),
                gridParams,
                eventTimeOutOfOrder,
                publishedAtMs,
                limitDelta,
                seedPayoutYuan,
                maxWorstLossYuan,
                emitSummarySnapshot,
                emitLimitSnapshot,
                emitBusinessSnapshot,
                emitDecision,
                mainOut,
                limitTag,
                businessTag,
                decisionTag,
                sideOutput);
    }

    static void emit(
            EnrichedFootballOrder trigger,
            String matchKey,
            MatchTriggerAcceptance acceptance,
            List<FootballSportsOrder> allSeenOrders,
            ScoreGridParams gridParams,
            boolean eventTimeOutOfOrder,
            long publishedAtMs,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            boolean emitSummarySnapshot,
            boolean emitLimitSnapshot,
            boolean emitBusinessSnapshot,
            boolean emitDecision,
            Collector<String> mainOut,
            OutputTag<String> limitTag,
            OutputTag<String> businessTag,
            OutputTag<String> decisionTag,
            SideOutputContext sideOutput) {
        ExposureSummary summaryExposure =
                MatchExposureAggregator.summarize(acceptance.acceptedOrders, gridParams.grid);
        ExposureSummary noRiskExposure =
                allSeenOrders == null || allSeenOrders.isEmpty()
                        ? null
                        : MatchExposureAggregator.summarize(allSeenOrders, gridParams.grid);
        int windowOrderCount = acceptance.acceptedOrders.size();
        String summaryJson =
                MatchExposureSummaryJson.summarySnapshotJson(
                        trigger,
                        matchKey,
                        acceptance.duplicateIgnored,
                        acceptance.triggerRejected,
                        eventTimeOutOfOrder,
                        windowOrderCount,
                        summaryExposure,
                        noRiskExposure,
                        gridParams,
                        publishedAtMs);
        if (emitSummarySnapshot) {
            mainOut.collect(summaryJson);
        }
        if (emitBusinessSnapshot && businessTag != null && sideOutput != null) {
            sideOutput.output(businessTag, MatchRiskBusinessSnapshotJson.fromSummary(summaryJson));
        }

        if (sideOutput != null && emitDecision && decisionTag != null) {
            sideOutput.output(
                    decisionTag,
                    RiskDecisionJson.fromAcceptance(
                            trigger,
                            acceptance,
                            noRiskExposure,
                            limitDelta,
                            seedPayoutYuan,
                            maxWorstLossYuan,
                            publishedAtMs,
                            "girisk-engine"));
        }

        if ((!emitLimitSnapshot && !emitBusinessSnapshot) || sideOutput == null) {
            return;
        }

        String limitJson =
                MatchLimitSummaryJson.limitSnapshotJson(
                        trigger,
                        matchKey,
                        windowOrderCount,
                        acceptance.duplicateIgnored,
                        limitDelta,
                        seedPayoutYuan,
                        acceptance.confirmedOrders,
                        acceptance.trialOrdersIncludingTrigger,
                        acceptance.rejectReason.name(),
                        ExposureLimitGate.maxExposureYuan(acceptance.trialExposure),
                        maxWorstLossYuan,
                        acceptance.postFeedbackMode,
                        publishedAtMs);

        if (emitLimitSnapshot && limitTag != null) {
            sideOutput.output(limitTag, limitJson);
        }
        if (emitBusinessSnapshot && businessTag != null) {
            sideOutput.output(businessTag, MatchRiskBusinessSnapshotJson.fromLimit(limitJson));
        }
    }

    interface SideOutputContext {
        <X> void output(OutputTag<X> outputTag, X value);
    }
}
