package com.girisk.flink.risk.settlement;

import com.girisk.flink.risk.excel.BetResultLabel;

/**
 * 亚洲盘线结算：整盘走水、半球全赢全输、四分盘半赢半输。
 *
 * <p>用于大小球（对比总进球）与亚洲让球（对比 goalDiff + line）。
 */
public final class AsianLineSettlement {

    private AsianLineSettlement() {}

    /**
     * @param value 实际值（总进球或净胜球差）
     * @param line 盘口线
     * @param higherWins true=大球/主队让球侧「越大越好」；false=小球/客队侧
     */
    public static BetResultLabel compare(double value, double line, boolean higherWins) {
        double frac = Math.round((Math.abs(line) - Math.floor(Math.abs(line))) * 100) / 100.0;
        double sign = line < 0 ? -1.0 : 1.0;
        double absLine = Math.abs(line);
        double absFrac = Math.round((absLine - Math.floor(absLine)) * 100) / 100.0;

        if (Math.abs(absFrac - 0.25) < 0.01 || Math.abs(absFrac - 0.75) < 0.01) {
            double low = Math.floor(absLine * 2.0) / 2.0;
            double high = low + 0.5;
            double effLow = sign * low;
            double effHigh = sign * high;
            if (line < 0) {
                effLow = -high;
                effHigh = -low;
            }
            return quarterCompare(value, effLow, effHigh, higherWins);
        }
        if (Math.abs(absFrac) < 0.01) {
            return integerCompare(value, line, higherWins);
        }
        return halfCompare(value, line, higherWins);
    }

    /** 亚洲让球：margin = goalDiff + lineFromHome，主队投注时 margin&gt;0 为赢。 */
    public static BetResultLabel settleHandicapMargin(int goalDiff, double lineFromHome, boolean homeBet) {
        if (isQuarterLine(lineFromHome)) {
            double[] bounds = quarterBounds(lineFromHome);
            double mLow = goalDiff + bounds[0];
            double mHigh = goalDiff + bounds[1];
            if (homeBet) {
                return marginFromTwoLegs(mLow, mHigh);
            }
            return marginFromTwoLegs(-mHigh, -mLow);
        }
        double margin = goalDiff + lineFromHome;
        if (Math.abs(margin) < 1e-9 && isIntegerLine(lineFromHome)) {
            return BetResultLabel.PUSH;
        }
        boolean win = homeBet ? margin > 0 : margin < 0;
        return win ? BetResultLabel.WIN : BetResultLabel.LOSE;
    }

    private static double[] quarterBounds(double lineFromHome) {
        double abs = Math.abs(lineFromHome);
        double lowAbs = Math.floor(abs * 2.0) / 2.0;
        double highAbs = lowAbs + 0.5;
        double low;
        double high;
        if (lineFromHome < 0) {
            low = -highAbs;
            high = -lowAbs;
        } else {
            low = lowAbs;
            high = highAbs;
        }
        return new double[] {low, high};
    }

    private static BetResultLabel marginFromTwoLegs(double mLow, double mHigh) {
        boolean winLow = mLow > 0;
        boolean winHigh = mHigh > 0;
        boolean loseLow = mLow < 0;
        boolean loseHigh = mHigh < 0;
        boolean pushLow = Math.abs(mLow) < 1e-9;
        boolean pushHigh = Math.abs(mHigh) < 1e-9;

        if (winLow && winHigh) {
            return BetResultLabel.WIN;
        }
        if (loseLow && loseHigh) {
            return BetResultLabel.LOSE;
        }
        if (winLow && pushHigh) {
            return BetResultLabel.WIN_HALF;
        }
        if (pushLow && loseHigh) {
            return BetResultLabel.LOSE_HALF;
        }
        if (winLow && loseHigh) {
            return BetResultLabel.WIN_HALF;
        }
        if (loseLow && winHigh) {
            return BetResultLabel.LOSE_HALF;
        }
        if (pushLow && pushHigh) {
            return BetResultLabel.PUSH;
        }
        if (pushLow && winHigh) {
            return BetResultLabel.WIN_HALF;
        }
        if (loseLow && pushHigh) {
            return BetResultLabel.LOSE_HALF;
        }
        return BetResultLabel.LOSE;
    }

    private static BetResultLabel quarterCompare(double value, double effLow, double effHigh, boolean higherWins) {
        if (higherWins) {
            if (value >= effHigh + 0.5) {
                return BetResultLabel.WIN;
            }
            if (Math.abs(value - effHigh) < 1e-9) {
                return BetResultLabel.WIN_HALF;
            }
            if (Math.abs(value - effLow) < 1e-9) {
                return BetResultLabel.LOSE_HALF;
            }
            return value > effHigh ? BetResultLabel.WIN : BetResultLabel.LOSE;
        }
        if (value <= effLow - 0.5) {
            return BetResultLabel.WIN;
        }
        if (Math.abs(value - effLow) < 1e-9) {
            return BetResultLabel.WIN_HALF;
        }
        if (Math.abs(value - effHigh) < 1e-9) {
            return BetResultLabel.LOSE_HALF;
        }
        return value < effLow ? BetResultLabel.WIN : BetResultLabel.LOSE;
    }

    private static BetResultLabel integerCompare(double value, double line, boolean higherWins) {
        if (higherWins) {
            if (value > line) {
                return BetResultLabel.WIN;
            }
            if (value < line) {
                return BetResultLabel.LOSE;
            }
            return BetResultLabel.PUSH;
        }
        if (value < line) {
            return BetResultLabel.WIN;
        }
        if (value > line) {
            return BetResultLabel.LOSE;
        }
        return BetResultLabel.PUSH;
    }

    private static BetResultLabel halfCompare(double value, double line, boolean higherWins) {
        if (higherWins) {
            return value > line ? BetResultLabel.WIN : BetResultLabel.LOSE;
        }
        return value < line ? BetResultLabel.WIN : BetResultLabel.LOSE;
    }

    static boolean isQuarterLine(double line) {
        double frac = Math.round((Math.abs(line) - Math.floor(Math.abs(line))) * 100) / 100.0;
        return Math.abs(frac - 0.25) < 0.01 || Math.abs(frac - 0.75) < 0.01;
    }

    static boolean isIntegerLine(double line) {
        return Math.abs(line - Math.rint(line)) < 1e-9;
    }
}
