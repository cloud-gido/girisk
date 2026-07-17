package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.model.LiveMatchScore;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * 滚球比分 Kafka 消息解析。
 *
 * <ul>
 *   <li>Genius envelope：{@code FixtureScoreUpdatedEvent}（{@code girisk.sportsdata.fixture.match.summary}）
 *   <li>Genius 旧版：{@code FootballMatchSummary}
 *   <li>简易 JSON / CSV
 * </ul>
 */
public final class LiveScoreEventParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> NOT_STARTED_PHASES =
            Set.of(
                    "PREMATCH",
                    "PRE_MATCH",
                    "NOTSTARTED",
                    "NOT_STARTED",
                    "BEFOREKICKOFF",
                    "BEFORE_KICK_OFF",
                    "BEFORE_KICKOFF");

    private static final Set<String> ENDED_PHASES =
            Set.of(
                    "FULLTIME",
                    "FULL_TIME",
                    "FULLTIMENORMALTIME",
                    "FULL_TIME_NORMAL_TIME",
                    "POSTMATCH",
                    "POST_MATCH",
                    "MATCHENDED",
                    "MATCH_ENDED",
                    "AFTEREXTRATIME",
                    "AFTER_EXTRA_TIME",
                    "PENALTIESFINISHED",
                    "PENALTIES_FINISHED",
                    "ENDED",
                    "FINISHED");

    private LiveScoreEventParser() {}

    public static LiveMatchScore parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("空消息");
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            return parseJson(trimmed);
        }
        return parseCsv(trimmed);
    }

    private static LiveMatchScore parseJson(String json) throws IllegalArgumentException {
        try {
            JsonNode root = MAPPER.readTree(json);
            String eventType = text(root, "eventType", "EventType");
            if ("FixtureScoreUpdatedEvent".equalsIgnoreCase(eventType)
                    || (root.has("payload") && root.has("fixtureId"))) {
                return parseFixtureScoreUpdatedEvent(root);
            }
            JsonNode summary = root.get("FootballMatchSummary");
            if (summary != null && !summary.isNull()) {
                return parseFootballMatchSummary(summary, root);
            }
            String fixtureId = text(root, "fixtureId", "FixtureId", "fixture_id");
            if (fixtureId.isEmpty()) {
                JsonNode fixture = root.get("Fixture");
                if (fixture != null && !fixture.isNull()) {
                    fixtureId = text(fixture, "Id", "id", "fixtureId");
                }
            }
            if (fixtureId.isEmpty()) {
                throw new IllegalArgumentException("缺少 fixtureId");
            }
            int[] score = parseScoreNode(root);
            long eventTimeMs = parseEventTime(root);
            return new LiveMatchScore(fixtureId, score[0], score[1], eventTimeMs);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("滚球比分 JSON 解析失败: " + ex.getMessage(), ex);
        }
    }

    /** Genius envelope：{@code FixtureScoreUpdatedEvent}。 */
    static LiveMatchScore parseFixtureScoreUpdatedEvent(JsonNode root) {
        String fixtureId = text(root, "fixtureId", "FixtureId");
        if (fixtureId.isEmpty()) {
            throw new IllegalArgumentException("FixtureScoreUpdatedEvent 缺少 fixtureId");
        }
        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("FixtureScoreUpdatedEvent 缺少 payload");
        }

        String phase = text(payload, "currentPhase", "CurrentPhase");
        boolean matchEnded = isEndedPhase(phase);
        boolean matchStarted = isStartedPhase(phase, matchEnded);

        JsonNode goals = payload.path("goals");
        boolean scoreCollected = goals.path("isCollected").asBoolean(false);
        int home = 0;
        int away = 0;
        JsonNode scoreNode = goals.path("score");
        if (!scoreNode.isMissingNode() && !scoreNode.isNull()) {
            home = scoreNode.path("home").asInt(scoreNode.path("Home").asInt(0));
            away = scoreNode.path("away").asInt(scoreNode.path("Away").asInt(0));
            if (home < 0) {
                home = 0;
            }
            if (away < 0) {
                away = 0;
            }
        } else {
            scoreCollected = false;
        }

        long eventTimeMs = parseIsoMillis(text(payload, "messageTimestampUtc", "MessageTimestampUtc"));
        if (eventTimeMs <= 0L) {
            eventTimeMs =
                    parseIsoMillis(
                            text(payload.path("clock"), "timestampUtc", "TimestampUtc"));
        }
        if (eventTimeMs <= 0L) {
            eventTimeMs = parseIsoMillis(text(root, "sourceSequence"));
        }

        return new LiveMatchScore(
                fixtureId, home, away, eventTimeMs, phase, matchStarted, matchEnded, scoreCollected);
    }

    static boolean isStartedPhase(String phase, boolean matchEnded) {
        if (phase == null || phase.isBlank()) {
            return false;
        }
        if (matchEnded) {
            return true;
        }
        return !NOT_STARTED_PHASES.contains(normalizePhase(phase));
    }

    static boolean isEndedPhase(String phase) {
        if (phase == null || phase.isBlank()) {
            return false;
        }
        return ENDED_PHASES.contains(normalizePhase(phase));
    }

    private static String normalizePhase(String phase) {
        return phase.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static LiveMatchScore parseFootballMatchSummary(JsonNode summary, JsonNode root) {
        String fixtureId = text(summary, "FixtureId", "fixtureId");
        if (fixtureId.isEmpty()) {
            throw new IllegalArgumentException("FootballMatchSummary 缺少 FixtureId");
        }
        JsonNode goals = summary.path("Goals").path("Score");
        if (goals.isMissingNode() || goals.isNull()) {
            throw new IllegalArgumentException("FootballMatchSummary 缺少 Goals.Score");
        }
        int home = goals.path("Home").asInt(-1);
        int away = goals.path("Away").asInt(-1);
        if (home < 0 || away < 0) {
            throw new IllegalArgumentException("Goals.Score Home/Away 无效");
        }
        long eventTimeMs = 0L;
        JsonNode header = root.get("Header");
        if (header != null && !header.isNull()) {
            eventTimeMs = parseIsoMillis(text(header, "TimeStampUtc", "timestampUtc"));
        }
        JsonNode clock = summary.path("Clock");
        if (eventTimeMs <= 0L && !clock.isMissingNode()) {
            eventTimeMs = parseIsoMillis(text(clock, "TimestampUtc", "timestampUtc"));
        }
        String phase = text(summary, "CurrentPhase", "currentPhase");
        boolean ended = isEndedPhase(phase);
        boolean started = isStartedPhase(phase, ended);
        return new LiveMatchScore(
                fixtureId, home, away, eventTimeMs, phase, started, ended, true);
    }

    private static int[] parseScoreNode(JsonNode root) {
        String scoreText = text(root, "score", "Score", "currentScore");
        if (!scoreText.isEmpty()) {
            return ScoreGridParams.parseScore(scoreText);
        }
        JsonNode scoreNode = root.get("score");
        if (scoreNode != null && scoreNode.isObject()) {
            int home = scoreNode.path("home").asInt(scoreNode.path("Home").asInt(-1));
            int away = scoreNode.path("away").asInt(scoreNode.path("Away").asInt(-1));
            if (home >= 0 && away >= 0) {
                return new int[] {home, away};
            }
        }
        int home = intField(root, "homeScore", "homeGoals", "home");
        int away = intField(root, "awayScore", "awayGoals", "away");
        if (home < 0 || away < 0) {
            throw new IllegalArgumentException("缺少 score / homeScore+awayScore");
        }
        return new int[] {home, away};
    }

    private static LiveMatchScore parseCsv(String line) {
        String[] parts = line.split(",", -1);
        if (parts.length < 3) {
            throw new IllegalArgumentException("滚球比分 CSV 至少 3 列: fixtureId,home,away");
        }
        String fixtureId = parts[0].trim();
        if (fixtureId.isEmpty()) {
            throw new IllegalArgumentException("fixtureId 为空");
        }
        int home = Integer.parseInt(parts[1].trim());
        int away = Integer.parseInt(parts[2].trim());
        long eventTimeMs = parts.length >= 4 && !parts[3].trim().isEmpty()
                ? Long.parseLong(parts[3].trim())
                : 0L;
        return new LiveMatchScore(fixtureId, home, away, eventTimeMs);
    }

    private static long parseEventTime(JsonNode root) {
        long ms = parseIsoMillis(text(root, "eventTimeMs", "timestampMs", "updatedAtMs"));
        if (ms > 0L) {
            return ms;
        }
        return parseIsoMillis(text(root, "eventTime", "timestamp", "updatedAt"));
    }

    private static long parseIsoMillis(String iso) {
        if (iso == null || iso.isBlank()) {
            return 0L;
        }
        String trimmed = iso.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            // epoch ms string or ISO-8601
        }
        try {
            return Instant.parse(trimmed).toEpochMilli();
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static int intField(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode n = root.get(key);
            if (n != null && n.isNumber()) {
                return n.asInt();
            }
        }
        return -1;
    }

    private static String text(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode n = root.get(key);
            if (n != null && !n.isNull() && !n.asText().isBlank()) {
                return n.asText().trim();
            }
        }
        return "";
    }

    public static String fixtureKey(LiveMatchScore score) {
        return score.fixtureId == null ? "" : score.fixtureId.trim();
    }

    public static String formatScore(LiveMatchScore score) {
        return String.format(Locale.ROOT, "%d:%d", score.homeGoals, score.awayGoals);
    }
}
