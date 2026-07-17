package com.girisk.sports.exposure;

import com.girisk.sports.dto.OutcomeLimitRow;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GroupLimitSnapshot(
        Map<String, BigDecimal> stakes,
        Map<String, BigDecimal> acceptMax,
        Map<String, BigDecimal> targets,
        Map<String, BigDecimal> maxAllowed,
        List<OutcomeLimitRow> rows
) {
    public static GroupLimitSnapshot from(
            String[] selections, List<BigDecimal> amounts, List<ProportionalLimitCalculator.LimitResult> results) {
        Map<String, BigDecimal> stakes = new LinkedHashMap<>();
        Map<String, BigDecimal> acceptMax = new LinkedHashMap<>();
        Map<String, BigDecimal> targets = new LinkedHashMap<>();
        Map<String, BigDecimal> maxAllowed = new LinkedHashMap<>();
        List<OutcomeLimitRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < selections.length; i++) {
            ProportionalLimitCalculator.LimitResult r = results.get(i);
            BigDecimal stake = amounts.get(i);
            stakes.put(selections[i], stake);
            acceptMax.put(selections[i], r.bMax());
            targets.put(selections[i], r.targetAmount());
            maxAllowed.put(selections[i], r.maxAllowed());
            rows.add(new OutcomeLimitRow(selections[i], stake, r.targetAmount(), r.maxAllowed(), r.bMax()));
        }
        return new GroupLimitSnapshot(stakes, acceptMax, targets, maxAllowed, rows);
    }
}
