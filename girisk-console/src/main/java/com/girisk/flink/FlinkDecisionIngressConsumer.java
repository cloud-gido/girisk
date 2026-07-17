package com.girisk.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.case_.model.RiskCase;
import com.girisk.case_.repository.RiskCaseRepository;
import com.girisk.common.decision.RiskDecisionCodes;
import com.girisk.decision.model.RiskDecisionLog;
import com.girisk.decision.repository.RiskDecisionLogRepository;
import com.girisk.event.repository.RiskEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consumes Flink {@code girisk.decision.v1}: audit log + REVIEW case creation.
 */
@Component
@ConditionalOnProperty(name = "girisk.kafka.enabled", havingValue = "true")
public class FlinkDecisionIngressConsumer {

    private static final Logger log = LoggerFactory.getLogger(FlinkDecisionIngressConsumer.class);

    private final ObjectMapper objectMapper;
    private final RiskDecisionLogRepository decisionLogRepository;
    private final RiskCaseRepository caseRepository;
    private final RiskEventRepository eventRepository;

    public FlinkDecisionIngressConsumer(
            ObjectMapper objectMapper,
            RiskDecisionLogRepository decisionLogRepository,
            RiskCaseRepository caseRepository,
            RiskEventRepository eventRepository) {
        this.objectMapper = objectMapper;
        this.decisionLogRepository = decisionLogRepository;
        this.caseRepository = caseRepository;
        this.eventRepository = eventRepository;
    }

    @KafkaListener(
            topics = "${girisk.kafka.flink-decision-topic:girisk.decision.v1}",
            groupId = "${girisk.kafka.flink-decision-group:girisk-console-flink-decision}")
    public void onDecision(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String decision = text(root, "decision");
            String orderId = text(root, "orderId");
            String userId = text(root, "userId");
            String operatorId = text(root, "operatorId");
            String fixtureId = text(root, "fixtureId");
            String reason = text(root, "reason");
            String requestId = "FLINK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String reasonsJson = root.has("reasons") ? root.get("reasons").toString() : "[]";
            String versionsJson = root.has("versions") ? root.get("versions").toString() : "{}";
            String evidenceJson = root.has("evidence") ? root.get("evidence").toString() : "{}";

            RiskDecisionLog logRow = new RiskDecisionLog(
                    null,
                    requestId,
                    orderId,
                    userId.isBlank() ? "UNKNOWN" : userId,
                    "SPORTS_BET",
                    "FLINK_ENGINE",
                    decision,
                    RiskDecisionCodes.REJECT.equals(decision) ? 80 : RiskDecisionCodes.REVIEW.equals(decision) ? 60 : 10,
                    RiskDecisionCodes.REJECT.equals(decision) ? "HIGH" : RiskDecisionCodes.REVIEW.equals(decision) ? "MEDIUM" : "LOW",
                    reasonsJson,
                    reason.isBlank() ? decision : reason,
                    BigDecimal.ZERO,
                    null,
                    null,
                    0,
                    "FLINK",
                    text(root, "traceId"),
                    fixtureId,
                    operatorId.isBlank() ? null : operatorId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    reasonsJson,
                    versionsJson,
                    evidenceJson,
                    LocalDateTime.now());
            long decisionId = decisionLogRepository.insert(logRow);

            eventRepository.insert(
                    "FLINK_DECISION",
                    RiskDecisionCodes.REJECT.equals(decision) ? "ERROR" : "INFO",
                    orderId,
                    userId,
                    "Flink 决策 " + decision,
                    reason);

            if (RiskDecisionCodes.REVIEW.equals(decision)) {
                String caseNo = "CASE-F-" + System.currentTimeMillis();
                RiskCase c = new RiskCase(
                        null,
                        caseNo,
                        decisionId,
                        orderId,
                        userId.isBlank() ? "UNKNOWN" : userId,
                        operatorId.isBlank() ? null : operatorId,
                        "PENDING",
                        "HIGH",
                        60,
                        "MEDIUM",
                        null,
                        null,
                        null,
                        LocalDateTime.now().plusHours(2),
                        "PENDING",
                        null,
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        null);
                caseRepository.insert(c);
                log.info("Created REVIEW case {} for Flink decision orderId={}", caseNo, orderId);
            }
        } catch (Exception e) {
            log.error("Failed to ingest Flink decision: {}", e.getMessage());
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }
}
