package com.girisk.configcenter.model;

import java.time.LocalDateTime;
import java.util.List;
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
        Map<String, Object> replayStats,
        /**
         * Flink Gate1 限额快照盘口（返彩+种子），与 {@code girisk:view:fixture:*.marketGroups} 同源。
         * Null/empty = 尚未写出。
         */
        List<Map<String, Object>> marketGroups,
        Double limitDelta,
        Double initialSeedPayoutYuan,
        Long marketGroupsUpdatedAt,
        /**
         * GROUPING SETS 下钻：{@code pre}/{@code live} → replayStats / marketGroups 等。
         * 顶层字段仍为整场 ALL。
         */
        Map<String, Object> segments
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
                null,
                null,
                null,
                null,
                null,
                null);
    }

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
            LocalDateTime updatedAt,
            Map<String, Object> replayStats) {
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
                replayStats,
                null,
                null,
                null,
                null,
                null);
    }

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
            LocalDateTime updatedAt,
            Map<String, Object> replayStats,
            List<Map<String, Object>> marketGroups,
            Double limitDelta,
            Double initialSeedPayoutYuan,
            Long marketGroupsUpdatedAt) {
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
                replayStats,
                marketGroups,
                limitDelta,
                initialSeedPayoutYuan,
                marketGroupsUpdatedAt,
                null);
    }
}
