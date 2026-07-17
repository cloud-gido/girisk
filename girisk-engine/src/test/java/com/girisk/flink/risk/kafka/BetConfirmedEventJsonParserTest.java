package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.excel.FootballBetSettlement;
import com.girisk.flink.risk.excel.BetResultLabel;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.fixture.FileFixtureMetadataLookup;
import com.girisk.flink.risk.fixture.FixtureOrderEnricher;
import com.girisk.flink.risk.time.OrderEventTimes;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetConfirmedEventJsonParserTest {

    @Test
    void parseBetConfirmedEvent() throws Exception {
        String json = loadSample();
        FootballSportsOrder o = BetConfirmedEventJsonParser.parse(json);
        assertEquals(1L, o.operatorId);
        assertEquals("bdfa0e9d-db9a-48df-a862-b81eb24ae2c5", o.eventId);
        assertEquals("999886001", o.fixtureId);
        assertEquals("318720381861392385", o.orderId);
        assertEquals("2026-05-29T12:01:00.574528Z", o.orderTime);
        assertEquals("NQTEST13146949BRL", o.userId);
        assertEquals("1X2", o.playType);
        assertEquals("无", o.handicapText);
        assertEquals("主胜", o.selection);
        assertEquals(1L, o.stakeYuan);
        assertEquals(24.0, o.odds, 0.001);
        assertEquals("单关", o.parlayType);
        assertTrue(FixtureOrderEnricher.needsFixtureDimension(o));
        assertTrue(OrderEventTimes.parseOrderTimeMillis(o.orderTime) > 0L);
        assertEquals(
                BetResultLabel.WIN,
                FootballBetSettlement.settle(o, 2, 1).label);
        assertEquals(
                BetResultLabel.LOSE,
                FootballBetSettlement.settle(o, 0, 0).label);
    }

    @Test
    void rejectNonConfirmedBetConfirmedEvent() throws Exception {
        String json = loadSample().replace("\"CONFIRMED\"", "\"PENDING\"");
        assertThrows(IllegalArgumentException.class, () -> BetConfirmedEventJsonParser.parse(json));
    }

    @Test
    void parseOrderRiskCheckEventPending() {
        String json =
                "{"
                        + "\"envelopeVersion\":\"1\","
                        + "\"operatorId\":1,"
                        + "\"sourceType\":\"TRADING\","
                        + "\"eventId\":\"18a723f2-7d9c-464f-b133-776de4a77eb1\","
                        + "\"eventType\":\"OrderRiskCheckEvent\","
                        + "\"aggregateType\":\"ORDER\","
                        + "\"aggregateId\":\"323705822959169537\","
                        + "\"payload\":{"
                        + "\"orderId\":\"323705822959169537\","
                        + "\"betType\":\"SINGLE\","
                        + "\"status\":\"PENDING\","
                        + "\"phase\":\"PRE_CONFIRM\","
                        + "\"stake\":50.0,"
                        + "\"playerId\":\"NQTEST13146949BRL\","
                        + "\"betTime\":\"2026-06-12T06:11:22.357020311Z\","
                        + "\"legs\":[{\"fixtureId\":\"13344525\","
                        + "\"legPick\":{\"type\":\"1X2\",\"line\":0.0,\"side\":\"HOME\"},"
                        + "\"price\":2.05}]"
                        + "}}";
        FootballSportsOrder o = BetConfirmedEventJsonParser.parse(json);
        assertEquals(1L, o.operatorId);
        assertEquals("18a723f2-7d9c-464f-b133-776de4a77eb1", o.eventId);
        assertEquals("323705822959169537", o.orderId);
        assertEquals("13344525", o.fixtureId);
        assertEquals(50L, o.stakeYuan);
        assertEquals("1X2", o.playType);
        assertEquals("主胜", o.selection);
        assertEquals(2.05, o.odds, 0.001);
        assertTrue(OrderEventTimes.parseOrderTimeMillis(o.orderTime) > 0L);
    }

    @Test
    void parseCombinationsEnvelopeWithMarketSelection() {
        String json =
                "{"
                        + "\"envelopeVersion\":\"1\","
                        + "\"operatorId\":1,"
                        + "\"eventId\":\"evt-1\","
                        + "\"eventType\":\"BetConfirmedEvent\","
                        + "\"orderId\":\"ord-xxx\","
                        + "\"payload\":{"
                        + "\"stake\":40000,"
                        + "\"betType\":\"SINGLE\","
                        + "\"betTime\":\"2026-05-14T08:11:08Z\","
                        + "\"status\":\"CONFIRMED\","
                        + "\"currency\":\"USD\","
                        + "\"userId\":\"92001\","
                        + "\"combinations\":[{\"legs\":[{\"fixtureId\":\"999886001\","
                        + "\"market\":{\"id\":9001,\"type\":\"AH\",\"period\":\"FT\",\"line\":-0.25},"
                        + "\"selection\":{\"selectionId\":\"307235991\",\"side\":\"HOME\"},"
                        + "\"price\":1.9}]}]}}";
        FootballSportsOrder o = BetConfirmedEventJsonParser.parse(json);
        assertEquals(1L, o.operatorId);
        assertEquals("evt-1", o.eventId);
        assertEquals("ord-xxx", o.orderId);
        assertEquals("92001", o.userId);
        assertEquals(40000L, o.stakeYuan);
        assertEquals("999886001", o.fixtureId);
        assertEquals("AH", o.playType);
        assertEquals("主队", o.selection);
        assertEquals("主队-0.25", o.handicapText.replace(" ", ""));
        assertEquals(1.9, o.odds, 0.001);
    }

    @Test
    void enrichFromFixtureDimFile() throws Exception {
        Path dim = Path.of(getClass().getResource("/fixture-dim.test.jsonl").toURI());
        var lookup = new FileFixtureMetadataLookup(dim);
        FootballSportsOrder o = BetConfirmedEventJsonParser.parse(loadSample());
        var meta = lookup.find(o.fixtureId).orElseThrow();
        FixtureOrderEnricher.apply(o, meta);
        assertEquals("模拟英超", o.league);
        assertEquals("星海联", o.homeTeam);
        assertEquals("山城竞技", o.awayTeam);
        assertEquals("2026-05-20 20:00:00", o.kickoffTime);
    }

    @Test
    void parseAsianHandicapLegPick() {
        String json =
                "{\"eventType\":\"BetConfirmedEvent\",\"aggregateId\":\"agg1\",\"payload\":{"
                        + "\"status\":\"CONFIRMED\",\"stake\":100.5,\"betTime\":\"2026-05-29T12:01:00Z\","
                        + "\"betType\":\"SINGLE\",\"playerId\":\"p1\","
                        + "\"legs\":[{\"fixtureId\":\"1\",\"legPick\":{\"type\":\"AH\",\"line\":-0.25,\"side\":\"HOME\"},\"price\":1.9}]"
                        + "}}";
        FootballSportsOrder o = BetConfirmedEventJsonParser.parse(json);
        assertEquals("agg1", o.orderId);
        assertEquals(101L, o.stakeYuan);
        assertEquals("AH", o.playType);
        assertEquals("主队-0.25", o.handicapText.replace(" ", ""));
        assertEquals("主队", o.selection);
    }

    @Test
    void mapLegPickSelectionFor1x2() {
        assertEquals("主胜", BetConfirmedEventJsonParser.mapLegPickSelection("1X2", "HOME"));
        assertEquals("客胜", BetConfirmedEventJsonParser.mapLegPickSelection("1X2", "AWAY"));
        assertEquals("平局", BetConfirmedEventJsonParser.mapLegPickSelection("1X2", "DRAW"));
    }

    private static String loadSample() throws Exception {
        return new String(
                BetConfirmedEventJsonParserTest.class
                        .getResourceAsStream("/bet-confirmed-event.sample.json")
                        .readAllBytes());
    }
}
