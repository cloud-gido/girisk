package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.excel.FootballBetSettlement;
import com.girisk.flink.risk.time.OrderEventTimes;

public final class ParseSampleOrdersMain {

    public static void main(String[] args) {
        String[] lines = {
            "13883500,FB202605180001,2026-05-18 10:12:00,U1001,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,胜平负,单关,无,主胜,1.86,¥100.00",
            "13883500,FB202605180003,2026-05-18 10:29:00,U1003,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,让球胜平负,单关,主队 -0.25,主队让胜,2.35,¥50.00",
            "13883500,FB202605180006,2026-05-18 10:57:00,U1006,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,让球胜平负,单关,客队 +0.75,客队让胜,1.98,¥120.00",
        };
        for (String line : lines) {
            var o = KafkaFootballOrderCsvParser.parse(line);
            OrderEventTimes.parseOrderTimeMillis(o.orderTime);
            var s = FootballBetSettlement.settle(o, 2, 1);
            System.out.printf(
                    "OK %s play=%s hc=%s sel=%s @2:1=%s%n",
                    o.orderId, o.playType, o.handicapText, o.selection, s.label);
        }
        System.out.println("ALL OK");
    }
}
