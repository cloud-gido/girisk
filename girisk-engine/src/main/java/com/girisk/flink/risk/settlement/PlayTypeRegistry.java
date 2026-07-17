package com.girisk.flink.risk.settlement;

import com.girisk.flink.risk.excel.FootballSportsOrder;

import java.util.Locale;

/**
 * 玩法字段多地区别名 → {@link BetMarketFamily}。
 *
 * <p>优先按 {@code playType} 匹配；无法识别时结合 {@code selection} / {@code handicapText} 推断。
 */
public final class PlayTypeRegistry {

    private PlayTypeRegistry() {}

    public static BetMarketFamily resolve(FootballSportsOrder order) {
        String play = normalize(order.playType);
        if (!play.isEmpty()) {
            BetMarketFamily byPlay = fromPlayType(play);
            if (byPlay != BetMarketFamily.UNKNOWN) {
                return byPlay;
            }
        }
        return inferFromSelectionAndHandicap(order);
    }

    public static String normalize(String playType) {
        if (playType == null) {
            return "";
        }
        return playType.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
    }

    private static BetMarketFamily fromPlayType(String play) {
        // 更长、更具体的玩法必须先于子串匹配（如「让球胜平负」包含「胜平负」）
        if (containsAny(
                play,
                "让球胜平负",
                "让球 胜平负",
                "3 way handicap",
                "three way handicap",
                "handicap 3way",
                "handicap 3 way")) {
            return BetMarketFamily.HANDICAP_THREE_WAY;
        }
        if (containsAny(
                play,
                "让球",
                "亚洲让球",
                "asian handicap",
                "ah",
                "让球盘",
                "spread")) {
            return BetMarketFamily.ASIAN_HANDICAP;
        }
        if (containsAny(play, "handicap") && !play.contains("3 way") && !play.contains("3way")) {
            return BetMarketFamily.ASIAN_HANDICAP;
        }
        if (containsAny(
                play,
                "大小球",
                "大小",
                "over under",
                "over/under",
                "o/u",
                "ou",
                "total goals",
                "totals",
                "goal line")) {
            return BetMarketFamily.OVER_UNDER;
        }
        if (!play.contains("让球")
                && containsAny(
                        play,
                        "胜平负",
                        "1x2",
                        "1 x 2",
                        "match result",
                        "full time result",
                        "moneyline",
                        "money line")) {
            return BetMarketFamily.MATCH_RESULT;
        }
        return BetMarketFamily.UNKNOWN;
    }

    private static BetMarketFamily inferFromSelectionAndHandicap(FootballSportsOrder order) {
        String sel = SelectionNormalizer.normalize(order.selection);
        if (SelectionNormalizer.isOverUnderSelection(sel)) {
            return BetMarketFamily.OVER_UNDER;
        }
        if (SelectionNormalizer.isHandicapThreeWaySelection(sel)) {
            return BetMarketFamily.HANDICAP_THREE_WAY;
        }
        if (SelectionNormalizer.isAsianHomeSide(sel) || SelectionNormalizer.isAsianAwaySide(sel)) {
            return BetMarketFamily.ASIAN_HANDICAP;
        }
        if (SelectionNormalizer.isMatchResultSelection(sel)) {
            return BetMarketFamily.MATCH_RESULT;
        }
        String handicap = order.handicapText == null ? "" : order.handicapText.trim();
        if (HandicapLines.looksLikeGoalLine(handicap)) {
            return BetMarketFamily.OVER_UNDER;
        }
        if (HandicapLines.looksLikeTeamHandicap(handicap)) {
            return BetMarketFamily.ASIAN_HANDICAP;
        }
        return BetMarketFamily.UNKNOWN;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
