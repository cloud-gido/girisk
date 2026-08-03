package com.girisk.sports.dto;

/**
 * 运营编辑赛事展示元数据（均可空 / 留白）。
 */
public record SportsMatchMetaRequest(
        String homeTeam,
        String awayTeam,
        String sportCode,
        String leagueCode,
        String leagueName
) {
}
