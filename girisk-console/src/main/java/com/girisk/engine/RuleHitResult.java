package com.girisk.engine;

import com.girisk.common.enums.RiskDecision;
import com.girisk.rule.model.RiskRule;

public record RuleHitResult(
        RiskRule rule,
        RiskDecision action,
        boolean matched
) {}
