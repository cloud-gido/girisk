package com.girisk.configcenter.model;

import java.time.LocalDateTime;
import java.util.Map;

public record RiskFixtureView(
        Long id,
        String fixtureId,
        String homeTeam,
        String awayTeam,
        String operatorId,
        int confirmedOrders,
        int pendingReserved,
        long worstLossCents,
        String worstScore,
        String liveScore,
        String marketSummaryJson,
        String riskLevel,
        LocalDateTime updatedAt,
        /** Optional replay / Flink stats (accept/reject/worst PnL…). Null when absent. */
        Map<String, Object> replayStats
) {
    public RiskFixtureView(
            Long id,
            String fixtureId,
            String homeTeam,
            String awayTeam,
            String operatorId,
            int confirmedOrders,
            int pendingReserved,
            long worstLossCents,
            String worstScore,
            String liveScore,
            String marketSummaryJson,
            String riskLevel,
            LocalDateTime updatedAt) {
        this(
                id,
                fixtureId,
                homeTeam,
                awayTeam,
                operatorId,
                confirmedOrders,
                pendingReserved,
                worstLossCents,
                worstScore,
                liveScore,
                marketSummaryJson,
                riskLevel,
                updatedAt,
                null);
    }
}
