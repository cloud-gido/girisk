package com.girisk.sports.dto;

import com.girisk.sports.model.SportsMatch;

import java.math.BigDecimal;
import java.util.List;

public record SportsDashboardSummary(
        int matchCount,
        int outcomeCount,
        int overLimitOutcomeCount,
        int limitModeMatchCount,
        BigDecimal totalStake,
        List<SportsMatch> matches,
        List<OverLimitOutcomeItem> overLimitOutcomes
) {
    public record OverLimitOutcomeItem(
            String matchCode,
            String homeTeam,
            String awayTeam,
            String marketType,
            String marketLabel,
            String line,
            String selection,
            BigDecimal stake,
            BigDecimal maxAllowedAmount
    ) {}
}
