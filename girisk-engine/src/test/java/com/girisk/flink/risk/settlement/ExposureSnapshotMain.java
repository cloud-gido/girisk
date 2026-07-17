package com.girisk.flink.risk.settlement;

import com.girisk.flink.risk.excel.FootballBetSettlement;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.MatchExposureAggregator.ExposureSummary;
import com.girisk.flink.risk.grid.MatchExposureAggregator.ScenarioExposure;
import com.girisk.flink.risk.grid.ScoreGridSpec;
import com.girisk.flink.risk.kafka.KafkaFootballOrderCsvParser;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** 本地核对：10 笔订单组合敞口（不依赖 JUnit）。 */
public final class ExposureSnapshotMain {

    public static void main(String[] args) {
        String[] lines = {
            "13883500,FB202605180001,2026-05-18 10:12,U1001,模拟英超,北岸 FC,南城竞技,2026-05-20 20:00,胜平负,单关,无,主胜,1.86,100",
            "13883500,FB202605180002,2026-05-18 10:20,U1002,模拟英超,北岸 FC,南城竞技,2026-05-20 20:00,大小球,单关,2.5球,大球,1.92,200",
            "13883500,FB202605180003,2026-05-18 10:29,U1003,模拟英超,北岸 FC,南城竞技,2026-05-20 20:00,让球胜平负,单关,主队-1,让胜,2.35,50",
            "13883500,FB202605180004,2026-05-18 10:37,U1004,模拟英超,北岸 FC,南城竞技,2026-05-20 20:00,胜平负,单关,无,平局,3.20,80",
            "13883500,FB202605180005,2026-05-18 10:44,U1005,模拟英超,北岸 FC,南城竞技,2026-05-20 20:00,大小球,单关,3.0球,小球,1.74,150",
            "13883500,FB202605180006,2026-05-18 10:57,U1006,模拟英超,北岸 FC,南城竞技,2026-05-20 20:00,让球胜平负,单关,客队+1,让负,1.98,120",
            "13883500,FB202605180007,2026-05-18 11:15,U1007,模拟英超,北岸 FC,南城竞技,2026-05-20 20:00,胜平负,单关,无,客胜,2.10,300",
            "13883500,FB202605180008,2026-05-18 11:28,U1008,模拟英超,北岸 FC,南城竞技,2026-05-20 20:00,大小球,单关,2.75球,大球,2.02,60",
            "13883500,FB202605180009,2026-05-18 11:46,U1009,模拟英超,北岸 FC,南城竞技,2026-05-20 20:00,让球胜平负,单关,主队-2,让平,3.75,40",
            "13883500,FB202605180010,2026-05-18 12:12,U1010,模拟英超,北岸 FC,南城竞技,2026-05-20 20:00,胜平负,单关,无,主胜,1.68,500"
        };

        ScoreGridSpec grid = ScoreGridSpec.fromBaseAndGridSize(0, 0, 6);
        List<FootballSportsOrder> orders =
                java.util.Arrays.stream(lines)
                        .map(KafkaFootballOrderCsvParser::parse)
                        .collect(Collectors.toList());

        ExposureSummary summary = MatchExposureAggregator.summarize(orders, grid);
        System.out.printf("最大庄家亏损(分)=%d (%.2f元)%n", summary.maxBookmakerLossCents, summary.maxBookmakerLossCents / 100.0);

        List<ScenarioExposure> sorted =
                summary.scenarios.stream()
                        .sorted(Comparator.comparingLong(s -> s.bookmakerPnlCents))
                        .collect(Collectors.toList());

        System.out.println("\n=== 庄家最亏 TOP10 比分 ===");
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            ScenarioExposure row = sorted.get(i);
            long loss = row.bookmakerPnlCents < 0 ? -row.bookmakerPnlCents : 0;
            System.out.printf(
                    "#%d %s 庄家P&L(分)=%d 亏损(分)=%d%n",
                    i + 1, row.scenario.scoreLabel(), row.bookmakerPnlCents, loss);
        }

        System.out.println("\n=== 逐笔在最大亏损比分上的贡献 ===");
        ScenarioExposure worst = sorted.get(0);
        int h = worst.scenario.homeGoals;
        int a = worst.scenario.awayGoals;
        System.out.printf("最坏比分: %s%n", worst.scenario.scoreLabel());
        long check = 0;
        for (FootballSportsOrder o : orders) {
            long pnl = FootballBetSettlement.settle(o, h, a).bookmakerPnlCents;
            check += pnl;
            System.out.printf(
                    "  %s %s %s 盘口=%s → 庄家P&L(分)=%d%n",
                    o.orderId, o.playType, o.selection, o.handicapText, pnl);
        }
        System.out.printf("合计(分)=%d%n", check);
    }
}
