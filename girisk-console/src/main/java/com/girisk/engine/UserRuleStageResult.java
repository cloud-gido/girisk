package com.girisk.engine;

import com.girisk.common.enums.RiskDecision;
import com.girisk.common.enums.RiskLevel;
import com.girisk.gateway.DecisionReason;

import java.util.List;

/** 用户规则段评估结果（不写审计日志）。 */
public record UserRuleStageResult(
        RiskDecision decision,
        int riskScore,
        RiskLevel riskLevel,
        List<String> hitRuleCodes,
        List<DecisionReason> reasons,
        String strategyCode,
        String reason
) {}
