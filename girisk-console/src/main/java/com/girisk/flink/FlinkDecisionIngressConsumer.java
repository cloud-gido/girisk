package com.girisk.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 消费 Flink {@code girisk.decision.v1}：MySQL 审计落库 + REVIEW 建单 + risk_event。
 * 责任盘盘口占用由 Flink 写 Redis {@code marketGroups}，不在此镜像 ExposureStore。
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
        String orderId = "";
        String traceId = "";
        try {
            JsonNode root = objectMapper.readTree(message);
            String decision = text(root, "decision");
            orderId = text(root, "orderId");
            String userId = text(root, "userId");
            String operatorId = text(root, "operatorId");
            String fixtureId = text(root, "fixtureId");
            String reason = text(root, "reason");
            traceId = text(root, "traceId");
            String requestId = !traceId.isBlank()
                    ? traceId
                    : (!orderId.isBlank()
                            ? "FLINK-" + orderId
                            : "FLINK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

            Optional<RiskDecisionLog> existing = decisionLogRepository.findByRequestId(requestId);
            if (existing.isEmpty() && !traceId.isBlank()) {
                existing = decisionLogRepository.findByTraceId(traceId);
            }
            if (existing.isPresent()) {
                auditEvent(
                        "DECISION_INGEST_DUP",
                        "INFO",
                        orderId,
                        userId,
                        "决策已入库（幂等跳过）",
                        "decisionId=" + existing.get().id()
                                + " requestId=" + requestId
                                + " decision=" + existing.get().decision()
                                + " fixtureId=" + fixtureId);
                log.info(
                        "Skip duplicate Flink decision orderId={} requestId={} decisionId={}",
                        orderId,
                        requestId,
                        existing.get().id());
                return;
            }

            String reasonsJson = root.has("reasons") ? root.get("reasons").toString() : "[]";
            String versionsJson = root.has("versions") ? root.get("versions").toString() : "{}";
            String marketJson = root.has("market") ? root.get("market").toString() : null;
            String featureJson = root.has("featureSnapshot")
                    ? root.get("featureSnapshot").toString()
                    : null;
            String evidenceJson = enrichEvidence(root);
            Long stakeCents = longOrNull(root, "stakeCents");
            Long payoutCents = longOrNull(root, "payoutCents");
            Long maxAccept = longOrNull(root, "maxAcceptableStakeCents");
            String odds = text(root, "odds");
            Integer latencyMs = resolveLatencyMs(root);
            String rejectReason = evidenceText(root, "rejectReason");
            boolean tradingRejected = evidenceBool(root, "tradingRejected");
            boolean maxBetRejected = evidenceBool(root, "maxBetRejected");
            boolean limitRejected = evidenceBool(root, "limitRejected");
            boolean exposureRejected = evidenceBool(root, "exposureRejected");

            int riskScore = RiskDecisionCodes.REJECT.equals(decision)
                    ? 80
                    : RiskDecisionCodes.REVIEW.equals(decision) ? 60 : 10;
            String riskLevel = RiskDecisionCodes.REJECT.equals(decision)
                    ? "HIGH"
                    : RiskDecisionCodes.REVIEW.equals(decision) ? "MEDIUM" : "LOW";

            RiskDecisionLog logRow = new RiskDecisionLog(
                    null,
                    requestId,
                    orderId.isBlank() ? "UNKNOWN" : orderId,
                    userId.isBlank() ? "UNKNOWN" : userId,
                    "SPORTS_BET",
                    "FLINK_ENGINE",
                    decision.isBlank() ? "UNKNOWN" : decision,
                    riskScore,
                    riskLevel,
                    reasonsJson,
                    reason.isBlank() ? decision : reason,
                    stakeCents == null ? BigDecimal.ZERO : BigDecimal.valueOf(stakeCents).movePointLeft(2),
                    null,
                    null,
                    latencyMs,
                    "FLINK",
                    traceId.isBlank() ? null : traceId,
                    fixtureId.isBlank() ? null : fixtureId,
                    operatorId.isBlank() ? null : operatorId,
                    marketJson,
                    stakeCents,
                    odds.isBlank() ? null : odds,
                    payoutCents,
                    maxAccept,
                    reasonsJson,
                    versionsJson,
                    featureJson,
                    evidenceJson,
                    LocalDateTime.now());
            long decisionId = decisionLogRepository.insert(logRow);

            String detail = "decisionId=" + decisionId
                    + " decision=" + decision
                    + " fixtureId=" + fixtureId
                    + " operatorId=" + operatorId
                    + " rejectReason=" + rejectReason
                    + " tradingRejected=" + tradingRejected
                    + " maxBetRejected=" + maxBetRejected
                    + " limitRejected=" + limitRejected
                    + " exposureRejected=" + exposureRejected
                    + " stakeCents=" + stakeCents
                    + " payoutCents=" + payoutCents
                    + " odds=" + odds
                    + " latencyMs=" + latencyMs
                    + " requestId=" + requestId;
            auditEvent(
                    "FLINK_DECISION",
                    RiskDecisionCodes.REJECT.equals(decision) ? "ERROR" : "INFO",
                    orderId,
                    userId,
                    "Flink 决策 " + decision + (fixtureId.isBlank() ? "" : " @" + fixtureId),
                    detail);

            log.info(
                    "Ingested Flink decision id={} orderId={} decision={} fixtureId={} rejectReason={}",
                    decisionId,
                    orderId,
                    decision,
                    fixtureId,
                    rejectReason);

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
                auditEvent(
                        "FLINK_REVIEW_CASE",
                        "WARN",
                        orderId,
                        userId,
                        "创建 REVIEW 工单 " + caseNo,
                        "decisionId=" + decisionId + " caseNo=" + caseNo);
                log.info("Created REVIEW case {} for Flink decision orderId={}", caseNo, orderId);
            }
        } catch (Exception e) {
            String snippet = message == null
                    ? ""
                    : message.substring(0, Math.min(500, message.length()));
            auditEvent(
                    "DECISION_INGEST_FAIL",
                    "ERROR",
                    orderId,
                    null,
                    "Flink 决策入库失败",
                    "traceId=" + traceId
                            + " err=" + e.getMessage()
                            + " raw=" + snippet);
            log.error(
                    "Failed to ingest Flink decision orderId={} traceId={}: {}",
                    orderId,
                    traceId,
                    e.getMessage(),
                    e);
            // 抛出让 DefaultErrorHandler 有限次重试；毒消息耗尽后跳过，避免整组卡住
            throw new IllegalStateException(
                    "decision ingest failed orderId=" + orderId + ": " + e.getMessage(), e);
        }
    }

    /** 把 productAudit 并入 evidence，便于 Console 回放不丢产品对账字段。 */
    private String enrichEvidence(JsonNode root) {
        try {
            ObjectNode evidence;
            if (root.has("evidence") && root.get("evidence").isObject()) {
                evidence = ((ObjectNode) root.get("evidence")).deepCopy();
            } else {
                evidence = objectMapper.createObjectNode();
            }
            if (root.has("productAudit") && !root.get("productAudit").isNull()) {
                evidence.set("productAudit", root.get("productAudit"));
            }
            if (root.has("decisionTimeMs") && !evidence.has("decisionTimeMs")) {
                evidence.put("decisionTimeMs", root.path("decisionTimeMs").asLong());
            }
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception e) {
            return root.has("evidence") ? root.get("evidence").toString() : "{}";
        }
    }

    private Integer resolveLatencyMs(JsonNode root) {
        if (root.has("latencyMs") && root.get("latencyMs").canConvertToInt()) {
            return root.get("latencyMs").intValue();
        }
        long decisionTimeMs = root.path("decisionTimeMs").asLong(0L);
        if (decisionTimeMs > 0L) {
            long lag = Instant.now().toEpochMilli() - decisionTimeMs;
            if (lag >= 0L && lag < Integer.MAX_VALUE) {
                return (int) lag;
            }
        }
        return 0;
    }

    private void auditEvent(
            String type, String severity, String orderId, String userId, String title, String detail) {
        try {
            eventRepository.insert(type, severity, orderId, userId, title, truncate(detail, 2000));
        } catch (Exception e) {
            log.warn("risk_event insert failed type={}: {}", type, e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String evidenceText(JsonNode root, String field) {
        JsonNode ev = root.path("evidence");
        if (!ev.isObject()) {
            return "";
        }
        return text(ev, field);
    }

    private static boolean evidenceBool(JsonNode root, String field) {
        JsonNode ev = root.path("evidence");
        if (!ev.isObject()) {
            return false;
        }
        JsonNode v = ev.get(field);
        return v != null && v.isBoolean() && v.asBoolean();
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }

    private static Long longOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || !v.canConvertToLong()) {
            return null;
        }
        return v.longValue();
    }
}
