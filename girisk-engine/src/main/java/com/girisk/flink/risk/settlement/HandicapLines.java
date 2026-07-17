package com.girisk.flink.risk.settlement;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 盘口文本解析（中/英、主队/客队、纯数字、大小球线）。 */
public final class HandicapLines {

    private static final Pattern SIGNED_NUMBER =
            Pattern.compile("([+\\-]?\\d+(?:\\.\\d+)?)");
    private static final Pattern HOME_PREFIX =
            Pattern.compile("^(?:主队|主|home|h)\\s*([+\\-]?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AWAY_PREFIX =
            Pattern.compile("^(?:客队|客|away|a)\\s*([+\\-]?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OU_PREFIX =
            Pattern.compile("^(?:o/u|ou|over/under|total|大小球?|进球)?\\s*([+\\-]?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

    private HandicapLines() {}

    public enum Side {
        HOME,
        AWAY,
        NEUTRAL
    }

    public static final class TeamLine {
        public final Side side;
        /** 加在对应球队进球上的让球数（主队 -1 → line=-1）。 */
        public final double line;

        public TeamLine(Side side, double line) {
            this.side = side;
            this.line = line;
        }

        /** 统一为「主队视角」的让球线（主队 -1 → -1；客队 +0.5 → 主队视角 -0.5）。 */
        public double lineFromHomePerspective() {
            if (side == Side.AWAY) {
                return -line;
            }
            return line;
        }
    }

    public static boolean isEmpty(String handicapText) {
        if (handicapText == null) {
            return true;
        }
        String t = handicapText.trim();
        return t.isEmpty() || "-".equals(t) || "无".equals(t) || "none".equalsIgnoreCase(t) || "n/a".equalsIgnoreCase(t);
    }

    public static boolean looksLikeGoalLine(String handicapText) {
        if (isEmpty(handicapText)) {
            return false;
        }
        String t = sanitize(handicapText);
        if (HOME_PREFIX.matcher(t).find() || AWAY_PREFIX.matcher(t).find()) {
            return false;
        }
        Matcher m = SIGNED_NUMBER.matcher(t);
        if (!m.find()) {
            return false;
        }
        double v = Double.parseDouble(m.group(1));
        return v >= 0;
    }

    public static boolean looksLikeTeamHandicap(String handicapText) {
        if (isEmpty(handicapText)) {
            return false;
        }
        String t = sanitize(handicapText);
        return HOME_PREFIX.matcher(t).find()
                || AWAY_PREFIX.matcher(t).find()
                || t.startsWith("+")
                || t.startsWith("-");
    }

    public static double parseGoalLine(String handicapText) {
        if (isEmpty(handicapText)) {
            throw new IllegalArgumentException("大小球盘口不能为空");
        }
        String t = sanitize(handicapText);
        Matcher ou = OU_PREFIX.matcher(t);
        if (ou.find()) {
            return Double.parseDouble(ou.group(1));
        }
        Matcher m = SIGNED_NUMBER.matcher(t);
        if (!m.find()) {
            throw new IllegalArgumentException("无法解析大小球盘口: " + handicapText);
        }
        return Double.parseDouble(m.group(1));
    }

    public static TeamLine parseTeamLine(String handicapText, boolean homeSideBet) {
        if (isEmpty(handicapText)) {
            return new TeamLine(Side.NEUTRAL, 0.0);
        }
        String t = sanitize(handicapText);

        Matcher home = HOME_PREFIX.matcher(t);
        if (home.find()) {
            return new TeamLine(Side.HOME, Double.parseDouble(home.group(1)));
        }
        Matcher away = AWAY_PREFIX.matcher(t);
        if (away.find()) {
            return new TeamLine(Side.AWAY, Double.parseDouble(away.group(1)));
        }

        Matcher num = SIGNED_NUMBER.matcher(t);
        if (num.find()) {
            double line = Double.parseDouble(num.group(1));
            return new TeamLine(homeSideBet ? Side.HOME : Side.AWAY, line);
        }
        throw new IllegalArgumentException("无法解析让球盘口: " + handicapText);
    }

    private static String sanitize(String raw) {
        return raw.trim()
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT)
                .replace('，', ',')
                .replace("球", "")
                .replace("goals", "")
                .replace("goal", "");
    }
}
