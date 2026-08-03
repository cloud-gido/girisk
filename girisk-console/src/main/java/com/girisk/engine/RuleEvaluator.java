package com.girisk.engine;

import com.girisk.common.enums.RiskDecision;
import com.girisk.common.enums.RiskLevel;
import com.girisk.list.repository.RiskListRepository;
import com.girisk.rule.model.RiskRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

@Component
public class RuleEvaluator {

    private final RiskListRepository listRepository;

    public RuleEvaluator(RiskListRepository listRepository) {
        this.listRepository = listRepository;
    }

    public RuleHitResult evaluate(RiskRule rule, RiskContext context) {
        if (!rule.enabled()) {
            return new RuleHitResult(rule, RiskDecision.PASS, false);
        }
        boolean matched = switch (rule.ruleType()) {
            case "LIST_HIT" -> evaluateListHit(rule, context);
            case "THRESHOLD" -> evaluateThreshold(rule, context);
            case "COMPOSITE" -> evaluateComposite(rule, context);
            default -> false;
        };
        RiskDecision action = matched ? RiskDecision.valueOf(rule.action()) : RiskDecision.PASS;
        return new RuleHitResult(rule, action, matched);
    }

    private boolean evaluateListHit(RiskRule rule, RiskContext context) {
        String fieldValue = String.valueOf(context.get(rule.field()));
        String listType = rule.threshold();
        return listRepository.exists(listType, rule.listKeyOrField(), fieldValue);
    }

    private boolean evaluateThreshold(RiskRule rule, RiskContext context) {
        Object raw = context.get(rule.field());
        if (raw == null) return false;
        String op = rule.operator();
        String threshold = rule.threshold();
        return switch (op) {
            case "GT" -> compareNumber(raw, threshold) > 0;
            case "GTE" -> compareNumber(raw, threshold) >= 0;
            case "LT" -> compareNumber(raw, threshold) < 0;
            case "LTE" -> compareNumber(raw, threshold) <= 0;
            case "EQ" -> String.valueOf(raw).equalsIgnoreCase(threshold);
            case "NE" -> !String.valueOf(raw).equalsIgnoreCase(threshold);
            case "IN" -> Set.of(threshold.split(",")).contains(String.valueOf(raw));
            default -> false;
        };
    }

    private boolean evaluateComposite(RiskRule rule, RiskContext context) {
        String[] fields = rule.field().split(",");
        String[] parts = rule.threshold().split(",");
        if (fields.length != parts.length) return false;
        if ("AND_GT".equals(rule.operator())) {
            for (int i = 0; i < fields.length; i++) {
                String f = fields[i].trim();
                Object val = context.get(f);
                if ("isNewUser".equals(f)) {
                    if (!Boolean.parseBoolean(parts[i].trim())) return false;
                    if (!Boolean.TRUE.equals(val)) return false;
                } else if (compareNumber(val, parts[i].trim()) <= 0) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private int compareNumber(Object raw, String threshold) {
        BigDecimal left = new BigDecimal(String.valueOf(raw));
        BigDecimal right = new BigDecimal(threshold);
        return left.compareTo(right);
    }
}
