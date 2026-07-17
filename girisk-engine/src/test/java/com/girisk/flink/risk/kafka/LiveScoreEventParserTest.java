package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.grid.LiveScoreGrid;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.model.LiveMatchScore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveScoreEventParserTest {

    private static final String FIXTURE_SCORE_UPDATED =
            "{"
                    + "\"envelopeVersion\":\"1\","
                    + "\"vendor\":\"GENIUS\","
                    + "\"eventId\":\"cd7a38fb-1949-4be4-a84b-9c7b0d24dbbc\","
                    + "\"eventType\":\"FixtureScoreUpdatedEvent\","
                    + "\"sourceSequence\":\"1781001423\","
                    + "\"fixtureId\":\"13981543\","
                    + "\"payload\":{"
                    + "\"clock\":{\"isClockRunning\":true,\"timeElapsedInPhase\":\"00:00:00\","
                    + "\"timestampUtc\":\"2026-06-09T09:53:29.315Z\"},"
                    + "\"currentPhase\":\"SecondHalf\","
                    + "\"goals\":{\"score\":{\"home\":2,\"away\":1},\"isCollected\":true,\"isReliable\":true},"
                    + "\"messageTimestampUtc\":\"2026-06-09T10:37:03.335Z\""
                    + "}"
                    + "}";

    @Test
    void parsesFixtureScoreUpdatedEvent() {
        LiveMatchScore s = LiveScoreEventParser.parse(FIXTURE_SCORE_UPDATED);
        assertEquals("13981543", s.fixtureId);
        assertEquals(2, s.homeGoals);
        assertEquals(1, s.awayGoals);
        assertEquals("SecondHalf", s.currentPhase);
        assertTrue(s.matchStarted);
        assertFalse(s.matchEnded);
        assertTrue(s.scoreCollected);
        assertEquals(2, s.effectiveHomeGoals());
        assertEquals(1, s.effectiveAwayGoals());
        assertTrue(s.eventTimeMs > 0L);
    }

    @Test
    void preMatchUsesZeroZeroEffectiveScore() {
        String json =
                "{"
                        + "\"eventType\":\"FixtureScoreUpdatedEvent\","
                        + "\"fixtureId\":\"100\","
                        + "\"payload\":{"
                        + "\"currentPhase\":\"PreMatch\","
                        + "\"goals\":{\"score\":{\"home\":0,\"away\":0},\"isCollected\":true}"
                        + "}"
                        + "}";
        LiveMatchScore s = LiveScoreEventParser.parse(json);
        assertFalse(s.matchStarted);
        assertEquals(0, s.effectiveHomeGoals());
        assertEquals(0, s.effectiveAwayGoals());
    }

    @Test
    void missingGoalsUsesZeroZero() {
        String json =
                "{"
                        + "\"eventType\":\"FixtureScoreUpdatedEvent\","
                        + "\"fixtureId\":\"100\","
                        + "\"payload\":{\"currentPhase\":\"FirstHalf\"}"
                        + "}";
        LiveMatchScore s = LiveScoreEventParser.parse(json);
        assertTrue(s.matchStarted);
        assertFalse(s.scoreCollected);
        assertEquals(0, s.effectiveHomeGoals());
        assertEquals(0, s.effectiveAwayGoals());
    }

    @Test
    void fullTimeKeepsFinalScore() {
        String json =
                "{"
                        + "\"eventType\":\"FixtureScoreUpdatedEvent\","
                        + "\"fixtureId\":\"100\","
                        + "\"payload\":{"
                        + "\"currentPhase\":\"FullTime\","
                        + "\"goals\":{\"score\":{\"home\":2,\"away\":1},\"isCollected\":true}"
                        + "}"
                        + "}";
        LiveMatchScore s = LiveScoreEventParser.parse(json);
        assertTrue(s.matchEnded);
        assertEquals(2, s.effectiveHomeGoals());
        assertEquals(1, s.effectiveAwayGoals());
    }

    @Test
    void parsesSimpleJson() {
        LiveMatchScore s =
                LiveScoreEventParser.parse(
                        "{\"fixtureId\":\"13883500\",\"homeScore\":2,\"awayScore\":1}");
        assertEquals("13883500", s.fixtureId);
        assertEquals(2, s.homeGoals);
        assertEquals(1, s.awayGoals);
    }

    @Test
    void parsesFootballMatchSummary() {
        String json =
                "{"
                        + "\"Header\":{\"TimeStampUtc\":\"2026-05-12T07:40:15.0290107Z\"},"
                        + "\"FootballMatchSummary\":{"
                        + "\"FixtureId\":\"13883500\","
                        + "\"Goals\":{\"Score\":{\"Home\":2,\"Away\":1}}"
                        + "}"
                        + "}";
        LiveMatchScore s = LiveScoreEventParser.parse(json);
        assertEquals("13883500", s.fixtureId);
        assertEquals(2, s.homeGoals);
        assertEquals(1, s.awayGoals);
    }

    @Test
    void liveGridDefaultsToZeroWhenNoLiveScore() {
        ScoreGridParams template = ScoreGridParams.fromMap(java.util.Map.of("score", "3:3", "grid", "6"));
        ScoreGridParams live = LiveScoreGrid.resolve(template, null);
        assertEquals(0, live.baseHome);
        assertEquals(0, live.baseAway);
        assertEquals(6, live.grid.homeSpan());
    }

    @Test
    void liveGridFromEffectiveScore() {
        ScoreGridParams template = ScoreGridParams.fromMap(java.util.Map.of("score", "0:0", "grid", "6"));
        LiveMatchScore score =
                new LiveMatchScore("1", 2, 1, 0L, "SecondHalf", true, false, true);
        ScoreGridParams live = LiveScoreGrid.resolve(template, score);
        assertEquals(2, live.baseHome);
        assertEquals(1, live.baseAway);
        assertEquals(6, live.grid.homeSpan());
        assertEquals(7, live.grid.homeMax);
        assertEquals(6, live.grid.awayMax);
    }

    @Test
    void liveGridPreMatchIgnoresRawScore() {
        ScoreGridParams template = ScoreGridParams.fromMap(java.util.Map.of("grid", "6"));
        LiveMatchScore score =
                new LiveMatchScore("1", 1, 0, 0L, "PreMatch", false, false, true);
        ScoreGridParams live = LiveScoreGrid.resolve(template, score);
        assertEquals(0, live.baseHome);
        assertEquals(0, live.baseAway);
    }
}
