package com.girisk.flink.risk.model;

import java.io.Serializable;
import java.util.Locale;

/** 滚球实时比分（按 fixtureId 关联场次）。 */
public final class LiveMatchScore implements Serializable {
    private static final long serialVersionUID = 1L;

    public String fixtureId;
    public int homeGoals;
    public int awayGoals;
    /** 比分事件时间（epoch ms）；无则 0。 */
    public long eventTimeMs;
    /** Genius {@code payload.currentPhase}；无则空。 */
    public String currentPhase;
    /** 是否已开赛（非 PreMatch / NotStarted 等）。 */
    public boolean matchStarted;
    /** 是否已完场（FullTime / PostMatch 等）。 */
    public boolean matchEnded;
    /** {@code payload.goals.isCollected}；无比分块时为 false。 */
    public boolean scoreCollected;

    public LiveMatchScore() {}

    public LiveMatchScore(String fixtureId, int homeGoals, int awayGoals, long eventTimeMs) {
        this(fixtureId, homeGoals, awayGoals, eventTimeMs, "", true, false, true);
    }

    public LiveMatchScore(
            String fixtureId,
            int homeGoals,
            int awayGoals,
            long eventTimeMs,
            String currentPhase,
            boolean matchStarted,
            boolean matchEnded,
            boolean scoreCollected) {
        this.fixtureId = fixtureId;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.eventTimeMs = eventTimeMs;
        this.currentPhase = currentPhase == null ? "" : currentPhase;
        this.matchStarted = matchStarted;
        this.matchEnded = matchEnded;
        this.scoreCollected = scoreCollected;
    }

    /** 网格基准比分：未开赛或无比分时固定 0:0。 */
    public int effectiveHomeGoals() {
        return effectiveGoals()[0];
    }

    public int effectiveAwayGoals() {
        return effectiveGoals()[1];
    }

    public int[] effectiveGoals() {
        if (!matchStarted || !scoreCollected) {
            return new int[] {0, 0};
        }
        return new int[] {homeGoals, awayGoals};
    }

    /** keyBy / broadcast 用，与 {@link com.girisk.flink.risk.kafka.LiveScoreEventParser#fixtureKey} 一致。 */
    public String getFixtureIdForKey() {
        return fixtureId == null ? "" : fixtureId.trim();
    }

    public String formatPhase() {
        return currentPhase == null || currentPhase.isBlank() ? "-" : currentPhase;
    }

    public String formatEffectiveScore() {
        return String.format(
                Locale.ROOT, "%d:%d", effectiveHomeGoals(), effectiveAwayGoals());
    }
}
