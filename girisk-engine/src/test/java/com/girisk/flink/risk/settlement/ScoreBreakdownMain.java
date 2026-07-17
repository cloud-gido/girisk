package com.girisk.flink.risk.settlement;

import com.girisk.flink.risk.excel.FootballBetSettlement;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.kafka.KafkaFootballOrderCsvParser;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** 指定假设比分下逐笔用户/庄家收益。 */
public final class ScoreBreakdownMain {

    public static void main(String[] args) {
        int home = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        int away = args.length > 1 ? Integer.parseInt(args[1]) : 1;

        String[] lines = {
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

        List<FootballSportsOrder> orders =
                Arrays.stream(lines).map(KafkaFootballOrderCsvParser::parse).collect(Collectors.toList());

        System.out.printf("假设比分 %d:%d（总进球 %d）%n%n", home, away, home + away);
        System.out.println("订单号\t玩法\t盘口\t投注项\t金额(元)\t结果\t用户盈利(元)\t庄家P&L(元)");

        long sumUser = 0;
        long sumBook = 0;
        for (FootballSportsOrder o : orders) {
            var s = FootballBetSettlement.settle(o, home, away);
            long userCents = -s.bookmakerPnlCents;
            sumUser += userCents;
            sumBook += s.bookmakerPnlCents;
            System.out.printf(
                    "%s\t%s\t%s\t%s\t%d\t%s\t%.2f\t%.2f%n",
                    o.orderId,
                    o.playType,
                    o.handicapText,
                    o.selection,
                    o.stakeYuan,
                    s.label.display,
                    userCents / 100.0,
                    s.bookmakerPnlCents / 100.0);
        }
        System.out.printf("%n用户合计盈利(元): %.2f%n", sumUser / 100.0);
        System.out.printf("庄家合计P&L(元): %.2f%n", sumBook / 100.0);
    }
}
