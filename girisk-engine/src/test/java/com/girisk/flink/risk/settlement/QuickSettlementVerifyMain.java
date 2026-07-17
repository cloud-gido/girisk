package com.girisk.flink.risk.settlement;

import com.girisk.flink.risk.excel.BetResultLabel;
import com.girisk.flink.risk.excel.FootballBetSettlement;
import com.girisk.flink.risk.excel.FootballSportsOrder;

/** 无 JUnit 时的快速断言（CI/离线环境）。 */
public final class QuickSettlementVerifyMain {

    public static void main(String[] args) {
        check("OU 2.75 @1:1", BetResultLabel.LOSE, order("大小球", "2.75球", "大球"), 1, 1);
        check("OU 2.75 @2:1", BetResultLabel.WIN_HALF, order("大小球", "2.75球", "大球"), 2, 1);
        check("3-way family", BetMarketFamily.HANDICAP_THREE_WAY, order("让球胜平负", "主队-1球", "让胜"));
        check("3-way @2:0", BetResultLabel.WIN, order("让球胜平负", "主队-1球", "让胜"), 2, 0);
        System.out.println("OK");
    }

    private static void check(String name, BetResultLabel expected, FootballSportsOrder o, int h, int a) {
        BetResultLabel actual = FootballBetSettlement.settle(o, h, a).label;
        if (actual != expected) {
            throw new IllegalStateException(name + " expected " + expected + " got " + actual);
        }
    }

    private static void check(String name, BetMarketFamily expected, FootballSportsOrder o) {
        BetMarketFamily actual = PlayTypeRegistry.resolve(o);
        if (actual != expected) {
            throw new IllegalStateException(name + " expected " + expected + " got " + actual);
        }
    }

    private static FootballSportsOrder order(String play, String handicap, String selection) {
        FootballSportsOrder o = new FootballSportsOrder();
        o.playType = play;
        o.handicapText = handicap;
        o.selection = selection;
        o.odds = 2.0;
        o.stakeYuan = 100;
        return o;
    }
}
