package com.girisk.flink.risk.fixture;

import java.io.Serializable;

/** 赛事维表：联赛 / 主客队 / 开赛时间。 */
public final class FixtureMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    public String fixtureId;
    public String league;
    public String homeTeam;
    public String awayTeam;
    public String kickoffTime;

    public FixtureMetadata() {}

    public FixtureMetadata(
            String fixtureId, String league, String homeTeam, String awayTeam, String kickoffTime) {
        this.fixtureId = fixtureId;
        this.league = league;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.kickoffTime = kickoffTime;
    }

    public boolean isComplete() {
        return nonEmpty(league) && nonEmpty(homeTeam) && nonEmpty(awayTeam) && nonEmpty(kickoffTime);
    }

    private static boolean nonEmpty(String s) {
        return s != null && !s.isBlank();
    }
}
