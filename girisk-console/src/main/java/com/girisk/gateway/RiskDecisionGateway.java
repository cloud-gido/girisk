package com.girisk.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.common.enums.RiskDecision;
import com.girisk.common.enums.RiskLevel;
import com.girisk.configcenter.model.RiskConfigRelease;
import com.girisk.configcenter.repository.RiskConfigReleaseRepository;
import com.girisk.decision.model.RiskDecisionLog;
import com.girisk.decision.repository.RiskDecisionLogRepository;
import com.girisk.engine.RiskEngineService;
import com.girisk.engine.UserRuleStageResult;
import com.girisk.event.repository.RiskEventRepository;
import com.girisk.config.SportsRiskProperties;
import com.girisk.sports.service.SportsBetRiskService;
import com.girisk.sports.stage.SportsLimitStageResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 统一风控决策网关：用户规则段 → 体育限额/敞口段 → 合并 reasons → 统一审计落库。
 */
@Service
public class RiskDecisionGateway {

    private final RiskEngineService riskEngineService;
    private final SportsBetRiskService sportsBetRiskService;
    private final RiskDecisionLogRepository decisionLogRepository;
    private final RiskEventRepository eventRepository;
    private final RiskConfigReleaseRepository configReleaseRepository;
    private final ObjectMapper objectMapper;
    private final TenantContext tenantContext;
    private final SportsRiskProperties sportsRiskProperties;

    public RiskDecisionGateway(
            RiskEngineService riskEngineService,
            SportsBetRiskService sportsBetRiskService,
            RiskDecisionLogRepository decisionLogRepository,
            RiskEventRepository eventRepository,
            RiskConfigReleaseRepository configReleaseRepository,
            ObjectMapper objectMapper,
            TenantContext tenantContext,
            SportsRiskProperties sportsRiskProperties) {
        this.riskEngineService = riskEngineService;
        this.sportsBetRiskService = sportsBetRiskService;
        this.decisionLogRepository = decisionLogRepository;
        this.eventRepository = eventRepository;
        this.configReleaseRepository = configReleaseRepository;
        this.objectMapper = objectMapper;
        this.tenantContext = tenantContext;
        this.sportsRiskProperties = sportsRiskProperties;
    }

    public RiskDecisionResponse decide(RiskDecisionRequest request) {
        return decide(request, "DECIDE");
    }

    public RiskDecisionResponse decide(RiskDecisionRequest request, String source) {
        tenantContext.setOperatorId(request.operatorId());
        try {
            return decideInternal(request, source);
        } finally {
            tenantContext.clear();
        }
    }

    private RiskDecisionResponse decideInternal(RiskDecisionRequest request, String source) {
        long start = System.currentTimeMillis();
        String requestId = "REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String traceId = request.traceId() != null ? request.traceId() : requestId;

        UserRuleStageResult userStage = riskEngineService.evaluateUserRules(request.toUserEvaluateRequest());
        List<DecisionReason> reasons = new ArrayList<>(userStage.reasons());
        RiskDecision decision = userStage.decision();
        String reason = userStage.reason();
        int score = userStage.riskScore();
        RiskLevel level = userStage.riskLevel();
        String strategyCode = userStage.strategyCode();

        SportsLimitStageResult sportsStage = null;
        Long maxAcceptableStakeCents = null;
        Long payoutCents = null;
        Boolean limitMode = null;
        Map<String, Object> sportsDetail = null;

        boolean runSports = sportsRiskProperties.isOnlineDecideEnabled()
                && request.hasSportsMarket()
                && decision != RiskDecision.REJECT;
        boolean doReserve = runSports
                && decision == RiskDecision.PASS
                && !Boolean.TRUE.equals(request.dryRun())
                && !Boolean.TRUE.equals(request.skipReserve());

        if (runSports) {
            sportsStage = sportsBetRiskService.evaluateStage(request.toSportsRequest(), doReserve);
            reasons.addAll(sportsStage.reasons());
            decision = RiskDecision.stricter(decision, sportsStage.decision());
            if (sportsStage.decision() != RiskDecision.PASS) {
                reason = sportsStage.reason();
            } else if (decision == RiskDecision.PASS) {
                reason = sportsStage.reason();
            }
            maxAcceptableStakeCents = sportsStage.maxAcceptableStakeCents();
            payoutCents = sportsStage.payoutCents();
            limitMode = sportsStage.limitMode();
            sportsDetail = new LinkedHashMap<>();
            sportsDetail.put("bMaxYuan", sportsStage.bMaxYuan());
            sportsDetail.put("groupStakes", sportsStage.groupStakes());
            sportsDetail.put("groupLimits", sportsStage.groupLimits());
            sportsDetail.put("matchExposure", sportsStage.matchExposure());
            sportsDetail.put("reserved", sportsStage.reserved());
            if (sportsStage.decision() == RiskDecision.REJECT || sportsStage.decision() == RiskDecision.LIMIT) {
                score = Math.max(score, 70);
                level = RiskLevel.fromScore(score);
            }
        }

        Map<String, Object> versions = loadVersions();
        Map<String, Object> featureSnapshot = buildFeatureSnapshot(request, userStage, sportsStage);
        String marketJson = null;
        if (request.hasSportsMarket()) {
            marketJson = toJson(Map.of(
                    "playType", request.marketType(),
                    "marketFamily", request.marketType(),
                    "line", request.line() != null ? request.line() : "",
                    "selection", request.selection()));
        }

        long latency = System.currentTimeMillis() - start;
        String hitRulesJson = toJson(reasons.stream().map(DecisionReason::ruleId).toList());

        RiskDecisionLog log = new RiskDecisionLog(
                null, requestId, request.orderId(), request.userId(), request.scenarioOrDefault(),
                strategyCode, decision.name(), score, level.name(),
                hitRulesJson, reason,
                request.amountYuan(), request.ip(), request.deviceId(), (int) latency, source,
                traceId, request.fixtureOrMatch(), request.operatorId(), marketJson,
                request.stakeCents(),
                request.odds() != null ? request.odds().toPlainString() : null,
                payoutCents, maxAcceptableStakeCents,
                toJson(reasons), toJson(versions), toJson(featureSnapshot),
                LocalDateTime.now());
        long logId = decisionLogRepository.insert(log);

        String caseNo = null;
        if (decision == RiskDecision.REVIEW) {
            caseNo = riskEngineService.createCase(logId, request.orderId(), request.userId(), request.operatorId(), userStage);
        }

        String severity = decision == RiskDecision.REJECT ? "ERROR"
                : (decision == RiskDecision.REVIEW || decision == RiskDecision.LIMIT) ? "WARN" : "INFO";
        eventRepository.insert("DECISION", severity, request.orderId(), request.userId(),
                "统一决策 " + decision.name(), reason);

        return new RiskDecisionResponse(
                traceId, requestId, request.orderId(), decision, score, level,
                reasons, versions, featureSnapshot,
                maxAcceptableStakeCents, payoutCents,
                request.fixtureOrMatch(), request.operatorId(), strategyCode,
                latency, caseNo, reason, limitMode, sportsDetail);
    }

    private Map<String, Object> loadVersions() {
        Map<String, Object> versions = new LinkedHashMap<>();
        versions.put("engineBuild", "girisk-1.0.0");
        RiskConfigRelease published = configReleaseRepository.findPublished().orElse(null);
        if (published != null) {
            versions.put("configEpoch", published.configEpoch());
            versions.put("paramSetVersion", published.paramSetVersion());
            versions.put("ruleSetVersion", published.ruleSetVersion());
        } else {
            versions.put("configEpoch", 1);
            versions.put("paramSetVersion", "ps-v1");
            versions.put("ruleSetVersion", "rs-v1");
        }
        return versions;
    }

    private Map<String, Object> buildFeatureSnapshot(
            RiskDecisionRequest request,
            UserRuleStageResult userStage,
            SportsLimitStageResult sportsStage) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("orderCount24h", request.orderCount24h());
        snap.put("amountSum24h", request.amountSum24h());
        snap.put("isNewUser", request.isNewUser());
        snap.put("deviceRiskScore", request.deviceRiskScore());
        snap.put("userDecision", userStage.decision().name());
        snap.put("userScore", userStage.riskScore());
        if (sportsStage != null) {
            snap.put("sportsDecision", sportsStage.decision().name());
            snap.put("reserved", sportsStage.reserved());
            snap.putAll(sportsStage.featureExtras());
        }
        return snap;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
