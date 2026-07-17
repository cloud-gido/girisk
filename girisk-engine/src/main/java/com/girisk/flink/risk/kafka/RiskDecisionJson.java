package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.girisk.common.decision.RiskDecisionCodes;
import com.girisk.flink.risk.limit.MatchTriggerAcceptance;
import com.girisk.flink.risk.model.EnrichedFootballOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Builds girisk.decision.v1 payload from Gate1/Gate2 acceptance. */
public final class RiskDecisionJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String SCHEMA_VERSION = "1";

    private RiskDecisionJson() {}

    public static String fromAcceptance(
            EnrichedFootballOrder trigger,
            MatchTriggerAcceptance acceptance,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            long publishedAtMs,
            String engineBuild) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("decisionTimeMs", publishedAtMs);
        root.put("orderId", nullToEmpty(trigger.order.orderId));
        root.put("fixtureId", nullToEmpty(trigger.order.fixtureId));
        root.put("operatorId", String.valueOf(trigger.order.operatorId));
        root.put("userId", nullToEmpty(trigger.order.userId));
        root.put("traceId", nullToEmpty(trigger.order.eventId));
        root.put("engineBuild", engineBuild == null ? "girisk-engine" : engineBuild);

        String decision;
        if (acceptance.duplicateIgnored) {
            decision = RiskDecisionCodes.PASS;
        } else if (acceptance.limitRejected || acceptance.exposureRejected) {
            decision = RiskDecisionCodes.REJECT;
        } else {
            decision = RiskDecisionCodes.PASS;
        }
        root.put("decision", decision);

        ArrayNode reasons = root.putArray("reasons");
        if (acceptance.limitRejected) {
            ObjectNode r = reasons.addObject();
            r.put("ruleId", "R_LIMIT_PROPORTIONAL");
            r.put("stage", "GATE1");
            r.put("action", RiskDecisionCodes.REJECT);
            r.put("message", "等比例限额拒单");
        }
        if (acceptance.exposureRejected) {
            ObjectNode r = reasons.addObject();
            r.put("ruleId", "R_EXPOSURE_WORST_LOSS");
            r.put("stage", "GATE2");
            r.put("action", RiskDecisionCodes.REJECT);
            r.put("message", "比分矩阵最差敞口超阈");
        }
        if (reasons.isEmpty()) {
            ObjectNode r = reasons.addObject();
            r.put("ruleId", "R_ACCEPT");
            r.put("stage", "GATE");
            r.put("action", RiskDecisionCodes.PASS);
            r.put("message", "限额与敞口均通过");
        }

        ObjectNode evidence = root.putObject("evidence");
        evidence.put("rejectReason", acceptance.rejectReason.name());
        evidence.put("shouldReject", acceptance.shouldReject);
        evidence.put("limitDelta", limitDelta);
        evidence.put("seedPayoutYuan", seedPayoutYuan);
        evidence.put("maxWorstLossYuan", maxWorstLossYuan);
        double trialWorstYuan =
                acceptance.trialExposure == null
                        ? 0
                        : acceptance.trialExposure.maxBookmakerLossCents / 100.0;
        evidence.put("trialWorstLossYuan", round2(trialWorstYuan));
        evidence.put("confirmedOrderCount", acceptance.confirmedOrders.size());
        evidence.put("pendingAware", acceptance.postFeedbackMode);
        double payoutYuan = trigger.order.stakeYuan * trigger.order.odds;
        evidence.put(
                "triggerPayoutYuan",
                BigDecimal.valueOf(payoutYuan).setScale(2, RoundingMode.HALF_UP).toPlainString());

        ObjectNode versions = root.putObject("versions");
        versions.put("paramSetVersion", "embedded");
        versions.put("ruleSetVersion", "embedded");
        versions.put("engineBuild", engineBuild == null ? "girisk-engine" : engineBuild);

        root.put(
                "reason",
                acceptance.shouldReject
                        ? ("拒单:" + acceptance.rejectReason.name())
                        : "限额与敞口均通过");
        return root.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
