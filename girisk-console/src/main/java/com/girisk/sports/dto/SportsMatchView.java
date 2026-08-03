package com.girisk.sports.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SportsMatchView(
        Long id,
        String matchCode,
        String homeTeam,
        String awayTeam,
        String sportCode,
        String leagueCode,
        String leagueName,
        BigDecimal exposureThreshold,
        boolean limitMode,
        BigDecimal currentExposure,
        BigDecimal delta,
        BigDecimal seedPayoutYuan,
        BigDecimal maxWorstLossYuan,
        BigDecimal maxBetPayoutYuan,
        boolean overrideActive,
        String status,
        LocalDateTime lastCheckAt,
        List<MarketGroupView> marketGroups
) {
    public record MarketGroupView(
            String marketType,
            String marketLabel,
            String line,
            Map<String, BigDecimal> stakes,
            /** 还能接收 b_max，用于接单判断 */
            Map<String, BigDecimal> limits,
            List<com.girisk.sports.dto.OutcomeLimitRow> outcomes
    ) {}
}
