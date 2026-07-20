package com.girisk.decision.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.decision.model.DecisionGateSummary;
import com.girisk.decision.model.DecisionGateSummary.Gate1;
import com.girisk.decision.model.DecisionGateSummary.Gate2;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 从 evidence + featureSnapshot 提取值班用 Gate1/Gate2 摘要。 */
@Component
public class DecisionGateSummarizer {

    private final ObjectMapper objectMapper;

    public DecisionGateSummarizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DecisionGateSummary summarize(String evidenceJson, String featureSnapshotJson) {
        Map<String, Object> evidence = asMap(evidenceJson);
        Map<String, Object> feature = asMap(featureSnapshotJson);
        @SuppressWarnings("unchecked")
        Map<String, Object> gate1Sel =
                evidence.get("gate1TriggerSelection") instanceof Map
                        ? (Map<String, Object>) evidence.get("gate1TriggerSelection")
                        : Map.of();

        String rejectReason = str(evidence.get("rejectReason"));
        boolean limitRejected = bool(evidence.get("limitRejected"));
        boolean exposureRejected = bool(evidence.get("exposureRejected"));

        Gate1 gate1 =
                new Gate1(
                        firstNonBlank(
                                str(gate1Sel.get("selectionLabel")), str(gate1Sel.get("selection"))),
                        str(gate1Sel.get("groupKey")),
                        num(gate1Sel.get("proposedPayout")),
                        num(gate1Sel.get("stakeBefore")),
                        num(gate1Sel.get("targetAmountBefore")),
                        num(gate1Sel.get("maxAllowedAmountBefore")),
                        num(gate1Sel.get("acceptMaxBefore")),
                        num(evidence.get("limitDelta")),
                        num(evidence.get("seedPayoutYuan")),
                        boolObj(gate1Sel.get("overLimitBefore")));

        @SuppressWarnings("unchecked")
        Map<String, Object> before =
                feature.get("beforeAccept") instanceof Map
                        ? (Map<String, Object>) feature.get("beforeAccept")
                        : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> trial =
                feature.get("trialAfterAccept") instanceof Map
                        ? (Map<String, Object>) feature.get("trialAfterAccept")
                        : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> after =
                feature.get("afterActual") instanceof Map
                        ? (Map<String, Object>) feature.get("afterActual")
                        : Map.of();

        Double trialWorstLoss = num(evidence.get("trialWorstLossYuan"));
        if (trialWorstLoss == null) {
            trialWorstLoss = num(trial.get("maxBookmakerLossYuan"));
        }
        String worstScore =
                firstNonBlank(str(trial.get("worstScore")), str(feature.get("worstScore")));

        Gate2 gate2 =
                new Gate2(
                        trialWorstLoss,
                        num(evidence.get("maxWorstLossYuan")),
                        worstScore,
                        num(before.get("worstBookmakerPnlYuan")),
                        num(trial.get("worstBookmakerPnlYuan")),
                        num(after.get("worstBookmakerPnlYuan")),
                        exposureRejected);

        return new DecisionGateSummary(rejectReason, limitRejected, exposureRejected, gate1, gate2);
    }

    private Map<String, Object> asMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static Double num(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean bool(Object v) {
        Boolean b = boolObj(v);
        return b != null && b;
    }

    private static Boolean boolObj(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }
}
