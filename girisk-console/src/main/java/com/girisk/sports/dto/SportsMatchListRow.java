package com.girisk.sports.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 敞口值班台赛事行：DB 基础字段 + 有效门控/限额 + 可选 Redis live。
 */
public record SportsMatchListRow(
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
        LocalDateTime updatedAt,
        boolean tradingEnabled,
        boolean limitGateEnabled,
        boolean exposureGateEnabled,
        String tradingSource,
        String limitGateSource,
        String exposureGateSource,
        String liveScore,
        String worstScore,
        Long worstLossCents,
        String riskLevel,
        Integer confirmedOrders
) {
}
