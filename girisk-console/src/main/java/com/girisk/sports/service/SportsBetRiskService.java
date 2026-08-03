package com.girisk.sports.service;

import com.girisk.common.enums.RiskDecision;
import com.girisk.common.exception.BusinessException;
import com.girisk.gateway.DecisionReason;
import com.girisk.sports.dto.SportsBetEvaluateRequest;
import com.girisk.sports.dto.SportsBetEvaluateResponse;
import com.girisk.sports.exposure.GroupLimitSnapshot;
import com.girisk.sports.exposure.LiabilityCalculator;
import com.girisk.sports.model.MarketGroupKey;
import com.girisk.sports.model.SportsBetLog;
import com.girisk.sports.model.SportsMarketType;
import com.girisk.sports.model.SportsMatch;
import com.girisk.sports.repository.SportsBetLogRepository;
import com.girisk.sports.repository.SportsMatchRepository;
import com.girisk.sports.stage.SportsLimitStageResult;
import com.girisk.sports.store.ExposureStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SportsBetRiskService {

    private final SportsMatchRepository matchRepository;
    private final SportsBetLogRepository betLogRepository;
    private final ExposureStore exposureStore;
    private final SportsExposureService exposureService;
    private final FixtureLimitParamsService limitParamsService;
    private final ScopeGateService scopeGateService;
    private final long reserveTtlSeconds;
    private final boolean limitDecisionEnabled;

    public SportsBetRiskService(
            SportsMatchRepository matchRepository,
            SportsBetLogRepository betLogRepository,
            ExposureStore exposureStore,
            SportsExposureService exposureService,
            FixtureLimitParamsService limitParamsService,
            ScopeGateService scopeGateService,
            @Value("${girisk.sports.reserve-ttl-seconds:30}") long reserveTtlSeconds,
            @Value("${girisk.sports.limit-decision-enabled:true}") boolean limitDecisionEnabled) {
        this.matchRepository = matchRepository;
        this.betLogRepository = betLogRepository;
        this.exposureStore = exposureStore;
        this.exposureService = exposureService;
        this.limitParamsService = limitParamsService;
        this.scopeGateService = scopeGateService;
        this.reserveTtlSeconds = reserveTtlSeconds;
        this.limitDecisionEnabled = limitDecisionEnabled;
    }

    /** Gateway 编排用：限额 + 敞口段。 */
    public SportsLimitStageResult evaluateStage(SportsBetEvaluateRequest req, boolean doReserve) {
        SportsMatch match = matchRepository.findByCode(req.matchCode())
                .orElseThrow(() -> new BusinessException("比赛不存在: " + req.matchCode()));
        ScopeGateService.EffectiveGates gates = scopeGateService.resolveForMatch(match);
        if (!gates.tradingEnabled()) {
            throw new BusinessException("比赛未开放投注（总开关关闭 · " + gates.tradingSource() + "）");
        }

        SportsMarketType marketType = SportsMarketType.from(req.marketType());
        MarketGroupKey groupKey = MarketGroupKey.of(req.matchCode(), marketType, req.line());
        groupKey.validateSelection(req.selection());

        if (doReserve && !Boolean.TRUE.equals(req.dryRun()) && exposureStore.isOrderProcessed(req.orderId())) {
            throw new BusinessException("订单已处理: " + req.orderId());
        }

        FixtureLimitParamsService.EffectiveParams params = limitParamsService.resolve(match);
        double delta = params.delta().doubleValue();
        BigDecimal seedPayoutYuan = params.seedPayoutYuan();
        long maxWorstLossCents = params.maxWorstLossCents();
        BigDecimal maxBetPayoutYuan = params.maxBetPayoutYuan();

        BigDecimal odds = req.odds() != null && req.odds().compareTo(BigDecimal.ZERO) > 0
                ? req.odds() : BigDecimal.valueOf(2.0);
        BigDecimal payoutNew = LiabilityCalculator.payoutYuan(req.amount(), odds);

        Map<String, BigDecimal> groupPayouts = exposureStore.getGroupPayouts(groupKey);
        Map<String, BigDecimal> groupStakes = exposureStore.getGroupStakes(groupKey);

        LiabilityCalculator.LimitResult limitResult = LiabilityCalculator.calcBMaxWithSeed(
                req.selection(), marketType.selections(), groupPayouts, delta, seedPayoutYuan);
        BigDecimal bMax = limitResult.bMaxPayout();

        List<DecisionReason> reasons = new ArrayList<>();
        RiskDecision decision = RiskDecision.PASS;
        String reason = "限额与敞口通过";
        Long maxAcceptableStakeCents = null;
        boolean reserved = false;

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("bMaxPayoutYuan", bMax);
        evidence.put("payoutNewYuan", payoutNew);
        evidence.put("seedPayoutYuan", seedPayoutYuan);
        evidence.put("delta", delta);
        evidence.put("maxWorstLossYuan", params.maxWorstLossYuan());
        evidence.put("overrideActive", params.overrideActive());
        evidence.put("groupPayouts", groupPayouts);
        evidence.put("basis", "payout");
        Map<String, Object> gateEv = new LinkedHashMap<>();
        gateEv.put("tradingEnabled", gates.tradingEnabled());
        gateEv.put("limitGateEnabled", gates.limitGateEnabled());
        gateEv.put("exposureGateEnabled", gates.exposureGateEnabled());
        gateEv.put("tradingSource", gates.tradingSource());
        gateEv.put("limitGateSource", gates.limitGateSource());
        gateEv.put("exposureGateSource", gates.exposureGateSource());
        evidence.put("gates", gateEv);

        // Gate0: 单注绝对返彩上限
        if (gates.limitGateEnabled()
                && maxBetPayoutYuan != null
                && payoutNew.compareTo(maxBetPayoutYuan) > 0) {
            BigDecimal maxStake = maxBetPayoutYuan.divide(odds, 2, RoundingMode.FLOOR);
            if (maxStake.compareTo(BigDecimal.ZERO) < 0) maxStake = BigDecimal.ZERO;
            if (limitDecisionEnabled) {
                decision = RiskDecision.LIMIT;
                maxAcceptableStakeCents = LiabilityCalculator.toCents(maxStake);
                reason = String.format("本单返彩 %s 超过单注上限 %s，最多可接本金 %s",
                        payoutNew, maxBetPayoutYuan, maxStake);
            } else {
                decision = RiskDecision.REJECT;
                reason = String.format("本单返彩 %s 超过单注上限 %s", payoutNew, maxBetPayoutYuan);
            }
            Map<String, Object> betEv = new LinkedHashMap<>(evidence);
            betEv.put("maxBetPayoutYuan", maxBetPayoutYuan);
            reasons.add(new DecisionReason(
                    "R_LIMIT_MAX_BET", 1, "GATE0_MAX_BET", decision.name(), reason, betEv));
        }

        // Gate1: 返彩 >= b_max 拒（或 LIMIT）
        if (gates.limitGateEnabled()
                && decision == RiskDecision.PASS
                && payoutNew.compareTo(bMax) >= 0) {
            if (limitDecisionEnabled && bMax.compareTo(BigDecimal.ZERO) > 0) {
                decision = RiskDecision.LIMIT;
                BigDecimal maxStake = bMax.subtract(new BigDecimal("0.01"))
                        .divide(odds, 2, RoundingMode.FLOOR);
                if (maxStake.compareTo(BigDecimal.ZERO) < 0) maxStake = BigDecimal.ZERO;
                maxAcceptableStakeCents = LiabilityCalculator.toCents(maxStake);
                reason = String.format("本单返彩 %s 超过可接上限 %s，最多可接本金 %s",
                        payoutNew, bMax, maxStake);
            } else {
                decision = RiskDecision.REJECT;
                reason = String.format("本单返彩 %s ≥ 盘口可接上限 %s（返彩口径）", payoutNew, bMax);
            }
            reasons.add(new DecisionReason(
                    "R_LIMIT_PROPORTIONAL", 2, "GATE1_LIMIT", decision.name(), reason, evidence));
        }

        // Gate2: 试探加入本单后的最坏 liability
        if (gates.exposureGateEnabled() && decision == RiskDecision.PASS) {
            Map<String, BigDecimal> trialStakes = new HashMap<>(groupStakes);
            trialStakes.merge(req.selection(), req.amount(), BigDecimal::add);
            Map<String, BigDecimal> oddsMap = new HashMap<>();
            for (String sel : marketType.selections()) {
                oddsMap.put(sel, sel.equals(req.selection()) ? odds : BigDecimal.valueOf(2.0));
            }
            LiabilityCalculator.LiabilityResult liab =
                    LiabilityCalculator.calcMutualExclusionLiability(
                            marketType.selections(), trialStakes, oddsMap);
            if (liab.worstLiabilityCents() > maxWorstLossCents) {
                decision = RiskDecision.REJECT;
                reason = String.format("试探敞口最差净责任 %s 分超过阈值 %s 分（最差结果 %s）",
                        liab.worstLiabilityCents(), maxWorstLossCents, liab.worstSelection());
                Map<String, Object> expEv = new LinkedHashMap<>();
                expEv.put("worstLiabilityCents", liab.worstLiabilityCents());
                expEv.put("thresholdCents", maxWorstLossCents);
                expEv.put("worstSelection", liab.worstSelection());
                expEv.put("liabilityBySelection", liab.liabilityBySelectionCents());
                expEv.put("gates", gateEv);
                reasons.add(new DecisionReason(
                        "R_EXPOSURE_WORST_LOSS", 1, "GATE2_EXPOSURE", "REJECT", reason, expEv));
            } else {
                evidence.put("worstLiabilityCents", liab.worstLiabilityCents());
                evidence.put("worstSelection", liab.worstSelection());
            }
        } else if (!gates.exposureGateEnabled() && decision == RiskDecision.PASS) {
            evidence.put("exposureGateSkipped", true);
        }

        // 预占（仅 PASS；LIMIT/REJECT/REVIEW 不占）
        if (decision == RiskDecision.PASS && doReserve && !Boolean.TRUE.equals(req.dryRun())) {
            Optional<String> fail = exposureStore.tryReserve(
                    req.orderId(), groupKey, req.selection(),
                    req.amount(), payoutNew, bMax, reserveTtlSeconds);
            if (fail.isPresent()) {
                decision = RiskDecision.REJECT;
                reason = fail.get();
                reasons.add(new DecisionReason(
                        "R_RESERVE", 1, "GATE1_LIMIT", "REJECT", reason, evidence));
            } else {
                reserved = true;
                reason = String.format("限额模式通过并已预占，可接返彩上限 %s，本单返彩 %s", bMax, payoutNew);
            }
        } else if (decision == RiskDecision.PASS && !match.limitMode()) {
            // 兼容：未开 limitMode 且未预占时仍允许（Gateway 默认会预占）
            reason = "限额与敞口通过";
        }

        GroupLimitSnapshot groupSnapshot = exposureService.calcGroupLimitSnapshot(groupKey, delta);
        BigDecimal matchExposure = exposureStore.getMatchTotalStake(req.matchCode());
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("confirmedPayouts", groupPayouts);
        extras.put("bMaxPayoutYuan", bMax);

        return new SportsLimitStageResult(
                decision, reason, reasons, true,
                bMax, maxAcceptableStakeCents,
                LiabilityCalculator.toCents(payoutNew),
                groupStakes.getOrDefault(req.selection(), BigDecimal.ZERO),
                limitResult.totalPayout(),
                limitResult.targetPayout(),
                limitResult.maxAllowedPayout(),
                groupStakes,
                groupSnapshot.acceptMax(),
                groupSnapshot.targets(),
                groupSnapshot.maxAllowed(),
                matchExposure,
                params.maxWorstLossYuan(),
                reserved,
                extras);
    }

    /** @deprecated 请走 RiskDecisionGateway；保留兼容适配层。 */
    @Deprecated
    public SportsBetEvaluateResponse evaluate(SportsBetEvaluateRequest req) {
        long start = System.currentTimeMillis();
        String requestId = "SB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        SportsLimitStageResult stage = evaluateStage(req, !Boolean.TRUE.equals(req.dryRun()));

        SportsMatch match = matchRepository.findByCode(req.matchCode()).orElseThrow();
        if (!Boolean.TRUE.equals(req.dryRun())) {
            betLogRepository.insert(new SportsBetLog(
                    null, requestId, req.orderId(), req.matchCode(),
                    SportsMarketType.from(req.marketType()).name(),
                    req.line(), req.selection(), req.amount(), req.odds(),
                    stage.decision().name(), stage.bMaxYuan(), true, stage.reason(),
                    (int) (System.currentTimeMillis() - start), null));
            if (stage.reserved()) {
                exposureService.runExposureCheck(req.matchCode());
            }
        }

        return new SportsBetEvaluateResponse(
                requestId, req.orderId(), stage.decision().name(), stage.reason(),
                true, req.amount(), stage.bMaxYuan(), stage.currentStake(),
                stage.groupTotal(), stage.targetAmount(), stage.maxAllowedAmount(),
                SportsBetEvaluateResponse.stakesMap(stage.groupStakes()),
                SportsBetEvaluateResponse.stakesMap(stage.groupLimits()),
                SportsBetEvaluateResponse.stakesMap(stage.groupTargets()),
                SportsBetEvaluateResponse.stakesMap(stage.groupMaxAllowed()),
                stage.matchExposure(), match.exposureThreshold(),
                System.currentTimeMillis() - start);
    }
}
