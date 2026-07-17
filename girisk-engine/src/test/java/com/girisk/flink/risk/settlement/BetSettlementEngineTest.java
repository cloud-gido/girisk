package com.girisk.flink.risk.settlement;

import com.girisk.flink.risk.excel.BetResultLabel;
import com.girisk.flink.risk.excel.FootballBetSettlement;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BetSettlementEngineTest {

    @Test
    void legacyChinese1x2() {
        FootballSportsOrder o = order("胜平负", "无", "主胜");
        assertEquals(BetResultLabel.WIN, FootballBetSettlement.settle(o, 2, 1).label);
        assertEquals(BetResultLabel.LOSE, FootballBetSettlement.settle(o, 1, 1).label);
    }

    @Test
    void englishMatchResult() {
        FootballSportsOrder o = order("Match Result", "-", "Home");
        assertEquals(BetResultLabel.WIN, FootballBetSettlement.settle(o, 1, 0).label);
        o.selection = "Draw";
        assertEquals(BetResultLabel.WIN, FootballBetSettlement.settle(o, 0, 0).label);
    }

    @Test
    void overUnderQuarterLine() {
        FootballSportsOrder o = order("大小球", "2.75球", "大球");
        // 总球 2：低于 2.5/3.0 两半，全输
        assertEquals(BetResultLabel.LOSE, FootballBetSettlement.settle(o, 1, 1).label);
        // 总球 3：大 2.5 赢 + 大 3.0 走水 → 赢半
        assertEquals(BetResultLabel.WIN_HALF, FootballBetSettlement.settle(o, 2, 1).label);
        // 总球 4：全赢
        assertEquals(BetResultLabel.WIN, FootballBetSettlement.settle(o, 2, 2).label);
    }

    @Test
    void handicapThreeWayNotClassifiedAs1x2() {
        assertEquals(BetMarketFamily.HANDICAP_THREE_WAY, PlayTypeRegistry.resolve(order("让球胜平负", "主队-1", "让胜")));
    }

    @Test
    void overUnderEnglish() {
        FootballSportsOrder o = order("Over/Under", "O/U 2.5", "Over");
        assertEquals(BetResultLabel.WIN, FootballBetSettlement.settle(o, 2, 1).label);
        o.selection = "Under";
        assertEquals(BetResultLabel.LOSE, FootballBetSettlement.settle(o, 2, 1).label);
    }

    @Test
    void handicapThreeWay() {
        FootballSportsOrder o = order("让球胜平负", "主队-1球", "让胜");
        assertEquals(BetResultLabel.WIN, FootballBetSettlement.settle(o, 2, 0).label);
        assertEquals(BetResultLabel.LOSE, FootballBetSettlement.settle(o, 1, 0).label);
    }

    @Test
    void asianHandicapQuarterHome() {
        FootballSportsOrder o = order("亚洲让球", "主队-0.25球", "主队");
        assertEquals(BetResultLabel.LOSE_HALF, FootballBetSettlement.settle(o, 0, 0).label);
        assertEquals(BetResultLabel.WIN, FootballBetSettlement.settle(o, 1, 0).label);
    }

    @Test
    void asianHandicapAlias() {
        FootballSportsOrder o = order("Asian Handicap", "H -0.5", "Away");
        assertEquals(BetResultLabel.WIN, FootballBetSettlement.settle(o, 0, 1).label);
    }

    @Test
    void inferOverUnderFromSelection() {
        FootballSportsOrder o = order("未知玩法", "3.5", "Under");
        assertEquals(BetResultLabel.WIN, FootballBetSettlement.settle(o, 1, 1).label);
    }

    private static FootballSportsOrder order(String play, String handicap, String selection) {
        FootballSportsOrder o = new FootballSportsOrder();
        o.orderId = "T1";
        o.playType = play;
        o.handicapText = handicap;
        o.selection = selection;
        o.odds = 2.0;
        o.stakeYuan = 100;
        return o;
    }
}
