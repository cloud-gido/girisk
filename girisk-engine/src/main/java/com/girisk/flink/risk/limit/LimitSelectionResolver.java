package com.girisk.flink.risk.limit;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.settlement.BetMarketFamily;
import com.girisk.flink.risk.settlement.HandicapLines;
import com.girisk.flink.risk.settlement.PlayTypeRegistry;
import com.girisk.flink.risk.settlement.SelectionNormalizer;

import java.util.Locale;
import java.util.Optional;

/** 将订单映射到限额盘口组与标准选项键。 */
public final class LimitSelectionResolver {

    public static final class ResolvedOutcome {
        public final LimitMarketType marketType;
        public final String line;
        public final String selectionKey;

        public ResolvedOutcome(LimitMarketType marketType, String line, String selectionKey) {
            this.marketType = marketType;
            this.line = line;
            this.selectionKey = selectionKey;
        }
    }

    private LimitSelectionResolver() {}

    public static Optional<ResolvedOutcome> resolve(FootballSportsOrder order) {
        BetMarketFamily family = PlayTypeRegistry.resolve(order);
        switch (family) {
            case MATCH_RESULT:
                return resolveMatchResult(order);
            case OVER_UNDER:
                return resolveOverUnder(order);
            case HANDICAP_THREE_WAY:
                return resolveHandicapThreeWay(order);
            case ASIAN_HANDICAP:
                return resolveAsianHandicap(order);
            default:
                return Optional.empty();
        }
    }

    private static Optional<ResolvedOutcome> resolveMatchResult(FootballSportsOrder order) {
        String key = matchResultKey(order.selection);
        if (key == null) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedOutcome(LimitMarketType.ONE_X_TWO, "", key));
    }

    private static Optional<ResolvedOutcome> resolveOverUnder(FootballSportsOrder order) {
        String key = overUnderKey(order.selection);
        if (key == null) {
            return Optional.empty();
        }
        String line;
        try {
            line = normalizeLine(String.valueOf(HandicapLines.parseGoalLine(order.handicapText)));
        } catch (IllegalArgumentException ex) {
            line = normalizeLine(order.handicapText);
        }
        return Optional.of(new ResolvedOutcome(LimitMarketType.OVER_UNDER, line, key));
    }

    private static Optional<ResolvedOutcome> resolveHandicapThreeWay(FootballSportsOrder order) {
        String line = handicapLineKey(order.handicapText);
        if (SelectionNormalizer.isHandicapHomeWin(order.selection)) {
            return Optional.of(new ResolvedOutcome(LimitMarketType.HANDICAP, line, "home"));
        }
        if (SelectionNormalizer.isHandicapAwayWin(order.selection)) {
            return Optional.of(new ResolvedOutcome(LimitMarketType.HANDICAP, line, "away"));
        }
        if (SelectionNormalizer.isHandicapDraw(order.selection)) {
            return Optional.of(new ResolvedOutcome(LimitMarketType.HANDICAP_THREE_WAY, line, "draw"));
        }
        return Optional.empty();
    }

    private static Optional<ResolvedOutcome> resolveAsianHandicap(FootballSportsOrder order) {
        boolean homeSide =
                SelectionNormalizer.isAsianHomeSide(order.selection)
                        || SelectionNormalizer.isHomeWin(order.selection);
        boolean awaySide =
                SelectionNormalizer.isAsianAwaySide(order.selection)
                        || SelectionNormalizer.isAwayWin(order.selection);
        if (!homeSide && !awaySide) {
            return Optional.empty();
        }
        String key = homeSide ? "home" : "away";
        String line;
        try {
            HandicapLines.TeamLine tl = HandicapLines.parseTeamLine(order.handicapText, homeSide);
            line = normalizeLine(String.valueOf(tl.lineFromHomePerspective()));
        } catch (IllegalArgumentException ex) {
            line = normalizeLine(order.handicapText);
        }
        return Optional.of(new ResolvedOutcome(LimitMarketType.HANDICAP, line, key));
    }

    private static String matchResultKey(String selection) {
        if (SelectionNormalizer.isHomeWin(selection)) {
            return "home";
        }
        if (SelectionNormalizer.isDraw(selection)) {
            return "draw";
        }
        if (SelectionNormalizer.isAwayWin(selection)) {
            return "away";
        }
        return null;
    }

    private static String overUnderKey(String selection) {
        if (SelectionNormalizer.isOver(selection)) {
            return "over";
        }
        if (SelectionNormalizer.isUnder(selection)) {
            return "under";
        }
        return null;
    }

    private static String handicapLineKey(String handicapText) {
        if (HandicapLines.isEmpty(handicapText)) {
            return "";
        }
        try {
            HandicapLines.TeamLine tl = HandicapLines.parseTeamLine(handicapText, true);
            return normalizeLine(String.valueOf(tl.lineFromHomePerspective()));
        } catch (IllegalArgumentException ex) {
            return normalizeLine(handicapText);
        }
    }

    static String normalizeLine(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.isEmpty() || "-".equals(t) || "无".equals(t) || "none".equalsIgnoreCase(t)) {
            return "";
        }
        try {
            double v = Double.parseDouble(t.replace(',', '.'));
            if (v == Math.rint(v)) {
                return String.valueOf((long) v);
            }
            return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
        } catch (NumberFormatException ex) {
            return t.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        }
    }

    public static String groupKey(LimitMarketType type, String line) {
        return type.name() + "|" + (line == null ? "" : line);
    }
}
