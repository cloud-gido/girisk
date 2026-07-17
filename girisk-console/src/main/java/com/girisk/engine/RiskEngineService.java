package com.girisk.engine;

import com.girisk.case_.model.RiskCase;
import com.girisk.case_.repository.RiskCaseRepository;
import com.girisk.common.dto.RiskEvaluateRequest;
import com.girisk.common.dto.RiskEvaluateResponse;
import com.girisk.common.enums.RiskDecision;
import com.girisk.common.enums.RiskLevel;
import com.girisk.decision.model.RiskDecisionLog;
import com.girisk.decision.repository.RiskDecisionLogRepository;
import com.girisk.event.repository.RiskEventRepository;
import com.girisk.gateway.DecisionReason;
import com.girisk.rule.model.RiskRule;
import com.girisk.rule.repository.RiskRuleRepository;
import com.girisk.strategy.model.RiskStrategy;
import com.girisk.strategy.repository.RiskStrategyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RiskEngineService {

    private static final int REVIEW_THRESHOLD = 60;
    private static final int REJECT_THRESHOLD = 85;

    private final RiskStrategyRepository strategyRepository;
    private final RiskRuleRepository ruleRepository;
    private final RuleEvaluator ruleEvaluator;
    private final RiskDecisionLogRepository decisionLogRepository;
    private final RiskCaseRepository caseRepository;
    private final RiskEventRepository eventRepository;

    public RiskEngineService(
            RiskStrategyRepository strategyRepository,
            RiskRuleRepository ruleRepository,
            RuleEvaluator ruleEvaluator,
            RiskDecisionLogRepository decisionLogRepository,
            RiskCaseRepository caseRepository,
            RiskEventRepository eventRepository) {
        this.strategyRepository = strategyRepository;
        this.ruleRepository = ruleRepository;
        this.ruleEvaluator = ruleEvaluator;
        this.decisionLogRepository = decisionLogRepository;
        this.caseRepository = caseRepository;
        this.eventRepository = eventRepository;
    }

    /** 仅跑用户规则段，不写决策日志（供 Gateway 编排）。 */
    public UserRuleStageResult evaluateUserRules(RiskEvaluateRequest request) {
        RiskContext context = RiskContext.from(request);
        List<RiskStrategy> strategies = strategyRepository.findEnabledByScenario(request.scenarioOrDefault());
        if (strategies.isEmpty()) {
            // SPORTS_BET 场景若无专用策略，回退 POST_ORDER
            if ("SPORTS_BET".equals(request.scenarioOrDefault())) {
                strategies = strategyRepository.findEnabledByScenario("POST_ORDER");
            }
        }
        if (strategies.isEmpty()) {
            return new UserRuleStageResult(
                    RiskDecision.PASS, 0, RiskLevel.LOW, List.of(), List.of(),
                    "DEFAULT", "无匹配策略，默认放行");
        }

        List<String> hitRuleCodes = new ArrayList<>();
        List<DecisionReason> reasons = new ArrayList<>();
        int totalScore = 0;
        RiskDecision finalDecision = RiskDecision.PASS;
        String reason = "综合评估通过";
        String strategyCode = strategies.get(0).code();

        for (RiskStrategy strategy : strategies) {
            List<RiskRule> rules = ruleRepository.findByStrategyId(strategy.id()).stream()
                    .sorted(Comparator.comparingInt(RiskRule::priority))
                    .toList();

            for (RiskRule rule : rules) {
                RuleHitResult hit = ruleEvaluator.evaluate(rule, context);
                if (!hit.matched()) {
                    continue;
                }
                hitRuleCodes.add(rule.code());
                totalScore = Math.min(100, totalScore + rule.scoreWeight());
                RiskDecision action = hit.action();
                reasons.add(new DecisionReason(
                        rule.code(), 1, "USER", action.name(),
                        "命中规则 " + rule.code() + ": " + rule.name(),
                        Map.of("scoreWeight", rule.scoreWeight(), "ruleType", rule.ruleType())));

                if (action == RiskDecision.REJECT) {
                    finalDecision = RiskDecision.REJECT;
                    reason = "命中规则 " + rule.code() + ": " + rule.name();
                    strategyCode = strategy.code();
                    break;
                }
                if (action == RiskDecision.PASS && "WHITELIST".equals(rule.threshold())) {
                    finalDecision = RiskDecision.PASS;
                    totalScore = 0;
                    reason = "命中白名单规则 " + rule.code();
                    strategyCode = strategy.code();
                    hitRuleCodes.clear();
                    hitRuleCodes.add(rule.code());
                    reasons.clear();
                    reasons.add(new DecisionReason(
                            rule.code(), 1, "USER", "PASS", reason,
                            Map.of("list", "WHITELIST")));
                    break;
                }
                if (action == RiskDecision.REVIEW || action == RiskDecision.CHALLENGE) {
                    if (finalDecision != RiskDecision.REJECT) {
                        finalDecision = action;
                        reason = "命中规则 " + rule.code() + ": " + rule.name();
                        strategyCode = strategy.code();
                    }
                }
            }
            if (finalDecision == RiskDecision.REJECT
                    || (finalDecision == RiskDecision.PASS && !hitRuleCodes.isEmpty()
                    && hitRuleCodes.stream().anyMatch(c -> c.startsWith("R002")))) {
                break;
            }
        }

        if (finalDecision != RiskDecision.REJECT && finalDecision != RiskDecision.PASS) {
            if (totalScore >= REJECT_THRESHOLD) {
                finalDecision = RiskDecision.REJECT;
                reason = "风险分 " + totalScore + " 超过拒绝阈值";
            } else if (totalScore >= REVIEW_THRESHOLD || finalDecision == RiskDecision.REVIEW) {
                finalDecision = RiskDecision.REVIEW;
                if ("综合评估通过".equals(reason)) {
                    reason = "风险分 " + totalScore + " 需人工审核";
                }
            } else {
                finalDecision = RiskDecision.PASS;
            }
        }

        return new UserRuleStageResult(
                finalDecision, totalScore, RiskLevel.fromScore(totalScore),
                List.copyOf(hitRuleCodes), List.copyOf(reasons), strategyCode, reason);
    }

    /** @deprecated 请使用 RiskDecisionGateway.decide；保留兼容旧调用方。 */
    @Deprecated
    public RiskEvaluateResponse evaluate(RiskEvaluateRequest request) {
        return evaluate(request, "SYNC");
    }

    @Deprecated
    public RiskEvaluateResponse evaluate(RiskEvaluateRequest request, String source) {
        long start = System.currentTimeMillis();
        String requestId = "REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UserRuleStageResult stage = evaluateUserRules(request);
        long latency = System.currentTimeMillis() - start;

        String hitRulesJson = toJsonArray(stage.hitRuleCodes());
        String reasonsJson = toJsonReasons(stage.reasons());
        String versionsJson = "{\"configEpoch\":1,\"paramSetVersion\":\"ps-v1\",\"ruleSetVersion\":\"rs-v1\",\"engineBuild\":\"girisk-1.0.0\"}";
        String featureSnapshotJson = "{\"orderCount24h\":" + nullSafe(request.orderCount24h())
                + ",\"amountSum24h\":" + nullSafe(request.amountSum24h())
                + ",\"isNewUser\":" + Boolean.TRUE.equals(request.isNewUser())
                + ",\"deviceRiskScore\":" + nullSafe(request.deviceRiskScore())
                + ",\"country\":\"" + (request.country() != null ? request.country() : "") + "\"}";

        RiskDecisionLog log = new RiskDecisionLog(
                null, requestId, request.orderId(), request.userId(), request.scenarioOrDefault(),
                stage.strategyCode(), stage.decision().name(), stage.riskScore(), stage.riskLevel().name(),
                hitRulesJson, stage.reason(),
                request.amount(), request.ip(), request.deviceId(), (int) latency, source,
                requestId, null, request.merchantId(), null,
                request.amount() != null ? request.amount().multiply(BigDecimal.valueOf(100)).longValue() : null,
                null, null, null,
                reasonsJson, versionsJson, featureSnapshotJson, LocalDateTime.now());
        long logId = decisionLogRepository.insert(log);

        String caseNo = null;
        if (stage.decision() == RiskDecision.REVIEW) {
            caseNo = createCase(logId, request.orderId(), request.userId(), stage);
        }

        String severity = stage.decision() == RiskDecision.REJECT ? "ERROR"
                : stage.decision() == RiskDecision.REVIEW ? "WARN" : "INFO";
        eventRepository.insert("DECISION", severity, request.orderId(), request.userId(),
                "订单风控" + decisionLabel(stage.decision()), stage.reason());

        return new RiskEvaluateResponse(
                requestId, request.orderId(), stage.decision(), stage.riskScore(), stage.riskLevel(),
                stage.hitRuleCodes(), stage.reason(), stage.strategyCode(), latency, caseNo);
    }

    public String createCase(long logId, String orderId, String userId, UserRuleStageResult stage) {
        return createCase(logId, orderId, userId, null, stage);
    }

    public String createCase(long logId, String orderId, String userId, String operatorId, UserRuleStageResult stage) {
        String caseNo = "CASE-" + System.currentTimeMillis() + "-" + CASE_SEQ.incrementAndGet();
        String priority = stage.riskLevel() == RiskLevel.HIGH || stage.riskLevel() == RiskLevel.CRITICAL ? "URGENT" : "HIGH";
        RiskCase riskCase = new RiskCase(
                null, caseNo, logId, orderId, userId, operatorId, "PENDING", priority,
                stage.riskScore(), stage.riskLevel().name(), null, null, null,
                LocalDateTime.now().plusHours(priority.equals("URGENT") ? 2 : 4),
                "NONE", null, null,
                LocalDateTime.now(), LocalDateTime.now(), null);
        caseRepository.insert(riskCase);
        eventRepository.insert("CASE_CREATED", "WARN", orderId, userId, "审核工单创建", caseNo);
        return caseNo;
    }

    private static final AtomicLong CASE_SEQ = new AtomicLong();

    private String decisionLabel(RiskDecision decision) {
        return switch (decision) {
            case PASS -> "通过";
            case REJECT -> "拒绝";
            case REVIEW -> "转审核";
            case LIMIT -> "限额";
            case CHALLENGE -> "挑战验证";
        };
    }

    private String toJsonArray(List<String> codes) {
        if (codes == null || codes.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(codes.get(i)).append('"');
        }
        return sb.append(']').toString();
    }

    private String toJsonReasons(List<DecisionReason> reasons) {
        if (reasons == null || reasons.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < reasons.size(); i++) {
            if (i > 0) sb.append(',');
            DecisionReason r = reasons.get(i);
            sb.append("{\"ruleId\":\"").append(r.ruleId())
                    .append("\",\"ruleVersion\":").append(r.ruleVersion())
                    .append(",\"stage\":\"").append(r.stage())
                    .append("\",\"action\":\"").append(r.action())
                    .append("\",\"message\":\"").append(escapeJson(r.message()))
                    .append("\",\"evidence\":").append(evidenceJson(r.evidence())).append('}');
        }
        return sb.append(']').toString();
    }

    private String evidenceJson(Map<String, Object> evidence) {
        if (evidence == null || evidence.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : evidence.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"').append(escapeJson(String.valueOf(v))).append('"');
            }
        }
        return sb.append('}').toString();
    }

    private static Object nullSafe(Object v) {
        return v == null ? "null" : v;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
