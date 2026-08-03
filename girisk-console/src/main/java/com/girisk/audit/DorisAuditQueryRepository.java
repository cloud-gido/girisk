package com.girisk.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.decision.model.RiskDecisionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only audit queries against Doris (MySQL protocol on FE:9030)。
 * 连接池由 {@link DorisAuditDataSourceManager} 管理（支持运行时开关与热加载）。
 */
@Repository
public class DorisAuditQueryRepository {

    private static final Logger log = LoggerFactory.getLogger(DorisAuditQueryRepository.class);

    private final DorisAuditDataSourceManager dataSourceManager;
    private final ObjectMapper objectMapper;

    public DorisAuditQueryRepository(
            DorisAuditDataSourceManager dataSourceManager, ObjectMapper objectMapper) {
        this.dataSourceManager = dataSourceManager;
        this.objectMapper = objectMapper;
    }

    public boolean available() {
        return dataSourceManager.available();
    }

    public List<RiskDecisionLog> findByOrderId(String orderId) {
        JdbcTemplate jdbc = dataSourceManager.jdbcTemplateOrNull();
        if (jdbc == null) {
            return List.of();
        }
        String table = decisionTable();
        try {
            return jdbc.query(
                    """
                    SELECT decision_time, order_id, trace_id, user_id, operator_id, fixture_id,
                           decision, engine_build, config_epoch, stake_cents, odds, payout_cents,
                           reasons, versions, evidence, feature_snapshot, raw
                    FROM %s
                    WHERE order_id = ?
                    ORDER BY decision_time DESC
                    """.formatted(table),
                    this::mapDecision,
                    orderId);
        } catch (Exception e) {
            log.warn("Doris findByOrderId failed table={}: {}", table, e.getMessage());
            return List.of();
        }
    }

    public Optional<RiskDecisionLog> findByTraceId(String traceId) {
        JdbcTemplate jdbc = dataSourceManager.jdbcTemplateOrNull();
        if (jdbc == null) {
            return Optional.empty();
        }
        String table = decisionTable();
        try {
            List<RiskDecisionLog> rows = jdbc.query(
                    """
                    SELECT decision_time, order_id, trace_id, user_id, operator_id, fixture_id,
                           decision, engine_build, config_epoch, stake_cents, odds, payout_cents,
                           reasons, versions, evidence, feature_snapshot, raw
                    FROM %s
                    WHERE trace_id = ?
                    ORDER BY decision_time DESC
                    LIMIT 1
                    """.formatted(table),
                    this::mapDecision,
                    traceId);
            return rows.stream().findFirst();
        } catch (Exception e) {
            log.warn("Doris findByTraceId failed table={}: {}", table, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Map<String, Object>> findConfigByEpoch(long configEpoch) {
        JdbcTemplate jdbc = dataSourceManager.jdbcTemplateOrNull();
        if (jdbc == null) {
            return Optional.empty();
        }
        String table = configTable();
        try {
            List<Map<String, Object>> rows = jdbc.query(
                    """
                    SELECT config_epoch, scope, approval_ticket, published_by, published_at,
                           param_set, rule_set, raw
                    FROM %s
                    WHERE config_epoch = ?
                    ORDER BY published_at DESC
                    LIMIT 1
                    """.formatted(table),
                    (rs, i) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("configEpoch", rs.getLong("config_epoch"));
                        m.put("scope", rs.getString("scope"));
                        m.put("approvalTicket", rs.getString("approval_ticket"));
                        m.put("publishedBy", rs.getString("published_by"));
                        Timestamp ts = rs.getTimestamp("published_at");
                        m.put("publishedAt", ts == null ? null : ts.toLocalDateTime().toString());
                        m.put("paramSetJson", rs.getString("param_set"));
                        m.put("ruleSetJson", rs.getString("rule_set"));
                        m.put("raw", rs.getString("raw"));
                        m.put("source", "doris");
                        return m;
                    },
                    configEpoch);
            return rows.stream().findFirst();
        } catch (Exception e) {
            log.warn("Doris findConfigByEpoch failed table={}: {}", table, e.getMessage());
            return Optional.empty();
        }
    }

    private String decisionTable() {
        return dataSourceManager.effectiveSettings().getDecisionTable();
    }

    private String configTable() {
        return dataSourceManager.effectiveSettings().getConfigTable();
    }

    private RiskDecisionLog mapDecision(ResultSet rs, int rowNum) throws SQLException {
        String raw = rs.getString("raw");
        if (raw != null && !raw.isBlank()) {
            RiskDecisionLog fromRaw = fromRawJson(raw, rs.getTimestamp("decision_time"));
            if (fromRaw != null) {
                return fromRaw;
            }
        }
        Timestamp ts = rs.getTimestamp("decision_time");
        LocalDateTime createdAt = ts == null ? LocalDateTime.now() : ts.toLocalDateTime();
        String decision = rs.getString("decision");
        String reasons = stringify(rs.getObject("reasons"));
        String versions = stringify(rs.getObject("versions"));
        String evidence = stringify(rs.getObject("evidence"));
        String feature = stringify(rs.getObject("feature_snapshot"));
        if ((feature == null || feature.isBlank()) && evidence != null) {
            feature = evidence;
        }
        Long stake = (Long) rs.getObject("stake_cents");
        Long payout = (Long) rs.getObject("payout_cents");
        String orderId = rs.getString("order_id");
        String traceId = rs.getString("trace_id");
        return new RiskDecisionLog(
                null,
                traceId == null ? orderId : traceId,
                orderId,
                rs.getString("user_id"),
                "SPORTS",
                "FLINK",
                decision,
                0,
                riskLevel(decision),
                null,
                firstReasonMessage(reasons),
                null,
                null,
                null,
                null,
                "DORIS",
                traceId,
                rs.getString("fixture_id"),
                rs.getString("operator_id"),
                null,
                stake,
                rs.getString("odds"),
                payout,
                null,
                reasons,
                versions,
                feature,
                evidence,
                createdAt);
    }

    private RiskDecisionLog fromRawJson(String raw, Timestamp decisionTime) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            String orderId = text(root, "orderId");
            String traceId = text(root, "traceId");
            String decision = text(root, "decision");
            String reasonsJson = root.has("reasons") ? root.get("reasons").toString() : null;
            String versionsJson = root.has("versions") ? root.get("versions").toString() : null;
            String evidenceJson = root.has("evidence") ? root.get("evidence").toString() : null;
            String featureJson = root.has("featureSnapshot")
                    ? root.get("featureSnapshot").toString()
                    : evidenceJson;
            String marketJson = root.has("market") ? root.get("market").toString() : null;
            Long stake = root.has("stakeCents") && root.get("stakeCents").canConvertToLong()
                    ? root.get("stakeCents").longValue()
                    : null;
            Long payout = root.has("payoutCents") && root.get("payoutCents").canConvertToLong()
                    ? root.get("payoutCents").longValue()
                    : null;
            Long maxAccept = root.has("maxAcceptableStakeCents") && !root.get("maxAcceptableStakeCents").isNull()
                    && root.get("maxAcceptableStakeCents").canConvertToLong()
                    ? root.get("maxAcceptableStakeCents").longValue()
                    : null;
            String odds = text(root, "odds");
            LocalDateTime createdAt = decisionTime == null
                    ? LocalDateTime.now()
                    : decisionTime.toLocalDateTime();
            if (root.has("decisionTimeMs") && root.get("decisionTimeMs").canConvertToLong()) {
                createdAt = java.time.Instant.ofEpochMilli(root.get("decisionTimeMs").longValue())
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDateTime();
            }
            Integer latency = root.has("latencyMs") && root.get("latencyMs").canConvertToInt()
                    ? root.get("latencyMs").intValue()
                    : null;
            return new RiskDecisionLog(
                    null,
                    traceId == null || traceId.isBlank() ? orderId : traceId,
                    orderId,
                    text(root, "userId"),
                    "SPORTS",
                    "FLINK",
                    decision,
                    0,
                    riskLevel(decision),
                    null,
                    firstReasonMessage(reasonsJson) != null
                            ? firstReasonMessage(reasonsJson)
                            : text(root, "reason"),
                    stake == null ? null : BigDecimal.valueOf(stake).movePointLeft(2),
                    null,
                    null,
                    latency,
                    "DORIS",
                    traceId,
                    text(root, "fixtureId"),
                    text(root, "operatorId"),
                    marketJson,
                    stake,
                    odds,
                    payout,
                    maxAccept,
                    reasonsJson,
                    versionsJson,
                    featureJson,
                    evidenceJson,
                    createdAt);
        } catch (Exception e) {
            log.debug("parse doris raw failed: {}", e.getMessage());
            return null;
        }
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String firstReasonMessage(String reasonsJson) {
        if (reasonsJson == null || reasonsJson.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> list =
                    objectMapper.readValue(reasonsJson, new TypeReference<>() {});
            if (list.isEmpty()) {
                return null;
            }
            Object msg = list.get(0).get("message");
            return msg == null ? null : String.valueOf(msg);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static String riskLevel(String decision) {
        if (decision == null) {
            return "LOW";
        }
        return switch (decision) {
            case "REJECT" -> "HIGH";
            case "REVIEW", "LIMIT", "CHALLENGE" -> "MEDIUM";
            default -> "LOW";
        };
    }
}
