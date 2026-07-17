package com.girisk.flink.risk.settlement;

import java.util.Locale;

/** 投注项多语言/多地区别名归一。 */
public final class SelectionNormalizer {

    private SelectionNormalizer() {}

    public static String normalize(String selection) {
        if (selection == null) {
            return "";
        }
        return selection.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    public static boolean isHomeWin(String selection) {
        String s = normalize(selection);
        return s.equals("主胜")
                || s.equals("主")
                || s.equals("home")
                || s.equals("h")
                || s.equals("1")
                || s.equals("home win")
                || s.equals("homewin");
    }

    public static boolean isAwayWin(String selection) {
        String s = normalize(selection);
        return s.equals("客胜")
                || s.equals("客")
                || s.equals("away")
                || s.equals("a")
                || s.equals("2")
                || s.equals("away win")
                || s.equals("awaywin");
    }

    public static boolean isDraw(String selection) {
        String s = normalize(selection);
        return s.equals("平局")
                || s.equals("和")
                || s.equals("和局")
                || s.equals("draw")
                || s.equals("x")
                || s.equals("tie")
                || s.equals("d");
    }

    public static boolean isOver(String selection) {
        String s = normalize(selection);
        return s.equals("大球")
                || s.equals("大")
                || s.equals("over")
                || s.equals("o")
                || s.startsWith("over ");
    }

    public static boolean isUnder(String selection) {
        String s = normalize(selection);
        return s.equals("小球")
                || s.equals("小")
                || s.equals("under")
                || s.equals("u")
                || s.startsWith("under ");
    }

    /** 让球后主队胜（体彩「让胜」/「主队让胜」）。 */
    public static boolean isHandicapHomeWin(String selection) {
        String s = normalize(selection);
        return s.equals("让胜")
                || s.equals("让主胜")
                || s.equals("主让胜")
                || s.equals("主队让胜")
                || s.equals("客队让负")
                || s.equals("h+")
                || s.equals("hw");
    }

    /** 让球后客队胜（体彩「让负」/「客队让胜」）。 */
    public static boolean isHandicapAwayWin(String selection) {
        String s = normalize(selection);
        return s.equals("让负")
                || s.equals("让客胜")
                || s.equals("客让胜")
                || s.equals("客队让胜")
                || s.equals("主队让负")
                || s.equals("a+")
                || s.equals("aw");
    }

    public static boolean isHandicapDraw(String selection) {
        String s = normalize(selection);
        return s.equals("让平")
                || s.equals("让和")
                || s.equals("主队让平")
                || s.equals("客队让平")
                || s.equals("hd");
    }

    /** 亚洲让球：投注主队一侧。 */
    public static boolean isAsianHomeSide(String selection) {
        String s = normalize(selection);
        return s.equals("主队")
                || s.equals("主")
                || s.equals("home")
                || s.equals("h")
                || isHomeWin(selection);
    }

    /** 亚洲让球：投注客队一侧。 */
    public static boolean isAsianAwaySide(String selection) {
        String s = normalize(selection);
        return s.equals("客队")
                || s.equals("客")
                || s.equals("away")
                || s.equals("a")
                || isAwayWin(selection);
    }

    public static boolean isMatchResultSelection(String selection) {
        return isHomeWin(selection) || isAwayWin(selection) || isDraw(selection);
    }

    public static boolean isOverUnderSelection(String selection) {
        return isOver(selection) || isUnder(selection);
    }

    public static boolean isHandicapThreeWaySelection(String selection) {
        return isHandicapHomeWin(selection)
                || isHandicapAwayWin(selection)
                || isHandicapDraw(selection);
    }
}
