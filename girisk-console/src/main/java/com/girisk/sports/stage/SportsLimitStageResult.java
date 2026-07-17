package com.girisk.sports.stage;

import com.girisk.common.enums.RiskDecision;
import com.girisk.gateway.DecisionReason;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 体育限额/敞口段评估结果（不写主审计；可由 Gateway 统一落库）。 */
public record SportsLimitStageResult(
        RiskDecision decision,
        String reason,
        List<DecisionReason> reasons,
        boolean limitMode,
        BigDecimal bMaxYuan,
        Long maxAcceptableStakeCents,
        Long payoutCents,
        BigDecimal currentStake,
        BigDecimal groupTotal,
        BigDecimal targetAmount,
        BigDecimal maxAllowedAmount,
        Map<String, BigDecimal> groupStakes,
        Map<String, BigDecimal> groupLimits,
        Map<String, BigDecimal> groupTargets,
        Map<String, BigDecimal> groupMaxAllowed,
        BigDecimal matchExposure,
        BigDecimal exposureThreshold,
        boolean reserved,
        Map<String, Object> featureExtras
) {}
