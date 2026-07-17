package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.excel.FootballBetSettlement;
import com.girisk.flink.risk.excel.BetResultLabel;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.time.OrderEventTimes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaFootballOrderCsvParserRobustnessTest {

    private static final String[] SAMPLE_LINES = {
        "13883500,FB202605180001,2026-05-18 10:12:00,U1001,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,胜平负,单关,无,主胜,1.86,¥100.00",
        "13883500,FB202605180002,2026-05-18 10:20:00,U1002,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,大小球,单关,2.5球,大球,1.92,¥200.00",
        "13883500,FB202605180003,2026-05-18 10:29:00,U1003,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,让球胜平负,单关,主队 -0.25,主队让胜,2.35,¥50.00",
        "13883500,FB202605180004,2026-05-18 10:37:00,U1004,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,胜平负,单关,无,平局,3.20,¥80.00",
        "13883500,FB202605180005,2026-05-18 10:44:00,U1005,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,大小球,单关,3.0球,小球,1.74,¥150.00",
        "13883500,FB202605180006,2026-05-18 10:57:00,U1006,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,让球胜平负,单关,客队 +0.75,客队让胜,1.98,¥120.00",
        "13883500,FB202605180007,2026-05-18 11:15:00,U1007,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,胜平负,单关,无,客胜,2.10,¥300.00",
        "13883500,FB202605180008,2026-05-18 11:28:00,U1008,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,大小球,单关,2.75球,大球,2.02,¥60.00",
        "13883500,FB202605180009,2026-05-18 11:46:00,U1009,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,让球胜平负,单关,主队 -0.75,主队让胜,3.75,¥40.00",
        "13883500,FB202605180010,2026-05-18 12:12:00,U1010,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,胜平负,单关,无,主胜,1.68,¥500.00"
    };

    @Test
    void parseAllSampleLines() {
        for (String line : SAMPLE_LINES) {
            assertDoesNotThrow(
                    () -> {
                        FootballSportsOrder o = KafkaFootballOrderCsvParser.parse(line);
                        OrderEventTimes.parseOrderTimeMillis(o.orderTime);
                        OrderEventTimes.parseKickoffTimeMillis(o.kickoffTime);
                        FootballBetSettlement.settle(o, 2, 1);
                    },
                    "failed: " + line);
        }
    }

    @Test
    void handicapSelectionAliases() {
        FootballSportsOrder o =
                KafkaFootballOrderCsvParser.parse(
                        "E1,T1,2026-05-18 10:00:00,U1,L,H,A,2026-05-20 20:00:00,让球胜平负,单关,主队 -0.25,主队让胜,2.0,100");
        assertEquals("主队-0.25", o.handicapText);
        assertEquals("主队让胜", o.selection);
        assertEquals(BetResultLabel.WIN, FootballBetSettlement.settle(o, 1, 0).label);
    }
}
