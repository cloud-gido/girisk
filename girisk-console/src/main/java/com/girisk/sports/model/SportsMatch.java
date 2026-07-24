package com.girisk.sports.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SportsMatch(
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
        String status,
        LocalDateTime lastCheckAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public String sportOrDefault() {
        return sportCode == null || sportCode.isBlank() ? "football" : sportCode;
    }

    public String leagueCodeOrDefault() {
        return leagueCode == null || leagueCode.isBlank() ? "UNKNOWN" : leagueCode;
    }

    public String leagueNameOrDefault() {
        return leagueName == null || leagueName.isBlank() ? "未分组联赛" : leagueName;
    }

    /** 展示用：空/UNKNOWN 视为未填。 */
    public boolean hasDisplayTeams() {
        return homeTeam != null && !homeTeam.isBlank() && awayTeam != null && !awayTeam.isBlank();
    }
}
