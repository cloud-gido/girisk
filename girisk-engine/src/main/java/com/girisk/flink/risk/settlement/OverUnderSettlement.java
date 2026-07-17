package com.girisk.flink.risk.settlement;

import com.girisk.flink.risk.excel.BetResultLabel;
import com.girisk.flink.risk.excel.FootballSportsOrder;

/** 大小球 / Over-Under 结算（保留原 Excel 规则，兼容 2.75 等四分盘）。 */
public final class OverUnderSettlement {

    private OverUnderSettlement() {}

    public static BetResultLabel settle(FootballSportsOrder order, int homeGoals, int awayGoals) {
        double line = HandicapLines.parseGoalLine(order.handicapText);
        double total = homeGoals + awayGoals;
        boolean over = SelectionNormalizer.isOver(order.selection);
        if (!over && !SelectionNormalizer.isUnder(order.selection)) {
            throw new IllegalArgumentException("大小球投注项无法识别: " + order.selection);
        }
        return overUnder(total, line, over);
    }

    /** 与历史 {@link com.girisk.flink.risk.excel.FootballBetSettlement} 行为一致。 */
    static BetResultLabel overUnder(double total, double line, boolean over) {
        double frac = Math.round((line - Math.floor(line)) * 100) / 100.0;
        if (Math.abs(frac - 0.25) < 0.01 || Math.abs(frac - 0.75) < 0.01) {
            double low = Math.floor(line * 2.0) / 2.0;
            double high = low + 0.5;
            if (over) {
                if (total >= high + 1) {
                    return BetResultLabel.WIN;
                }
                if (total == high) {
                    return BetResultLabel.WIN_HALF;
                }
                if (total == low) {
                    return BetResultLabel.LOSE_HALF;
                }
                return BetResultLabel.LOSE;
            }
            if (total <= low - 1) {
                return BetResultLabel.WIN;
            }
            if (total == low) {
                return BetResultLabel.WIN_HALF;
            }
            if (total == high) {
                return BetResultLabel.LOSE_HALF;
            }
            return BetResultLabel.LOSE;
        }
        if (Math.abs(frac) < 0.01) {
            if (over) {
                if (total > line) {
                    return BetResultLabel.WIN;
                }
                if (total < line) {
                    return BetResultLabel.LOSE;
                }
                return BetResultLabel.PUSH;
            }
            if (total < line) {
                return BetResultLabel.WIN;
            }
            if (total > line) {
                return BetResultLabel.LOSE;
            }
            return BetResultLabel.PUSH;
        }
        if (over) {
            if (total > line) {
                return BetResultLabel.WIN;
            }
            if (total < line) {
                return BetResultLabel.LOSE;
            }
            return BetResultLabel.PUSH;
        }
        if (total < line) {
            return BetResultLabel.WIN;
        }
        if (total > line) {
            return BetResultLabel.LOSE;
        }
        return BetResultLabel.PUSH;
    }
}
