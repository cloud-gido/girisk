package com.girisk.flink.risk.settlement;

import com.girisk.flink.risk.excel.BetResultLabel;
import com.girisk.flink.risk.excel.FootballBetSettlement;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.ScoreGridSpec;
import com.girisk.flink.risk.kafka.KafkaFootballOrderCsvParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 输出 36×10 输赢矩阵，并与表格样例抽查比对。 */
public final class MatrixCompareMain {

    private static final String[] LINES = {
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

    /** 表格样例中抽查单元格：比分 → 各订单期望 display */
    private static final java.util.Map<String, String[]> SPOT_CHECKS = new java.util.LinkedHashMap<>();

    static {
        SPOT_CHECKS.put(
                "0:0",
                new String[] {"输", "输", "输半", "赢", "赢", "赢", "输", "输", "输", "输"});
        SPOT_CHECKS.put(
                "1:0",
                new String[] {"赢", "输", "赢", "输", "赢", "输半", "输", "输", "赢半", "赢"});
        SPOT_CHECKS.put(
                "2:1",
                new String[] {"赢", "赢", "赢", "输", "走水", "输半", "输", "赢半", "赢半", "赢"});
        SPOT_CHECKS.put(
                "1:1",
                new String[] {"输", "输", "输半", "赢", "赢", "赢", "输", "输", "输", "输"});
        SPOT_CHECKS.put(
                "4:0",
                new String[] {"赢", "赢", "赢", "输", "输", "输", "输", "赢", "赢", "赢"});
        SPOT_CHECKS.put(
                "0:3",
                new String[] {"输", "赢", "输", "输", "赢", "赢", "赢", "赢", "输", "输"});
    }

    public static void main(String[] args) {
        List<FootballSportsOrder> orders = new ArrayList<>();
        for (String line : LINES) {
            orders.add(KafkaFootballOrderCsvParser.parse(line));
        }
        ScoreGridSpec grid = ScoreGridSpec.fromBaseAndGridSize(0, 0, 6);

        int mismatches = 0;
        System.out.println("=== 抽查比分（代码 vs 表格样例）===");
        for (var e : SPOT_CHECKS.entrySet()) {
            String[] p = e.getKey().split(":");
            int h = Integer.parseInt(p[0]);
            int a = Integer.parseInt(p[1]);
            String[] expected = e.getValue();
            System.out.printf("%n【%s】%n", e.getKey());
            for (int i = 0; i < orders.size(); i++) {
                String actual =
                        FootballBetSettlement.settle(orders.get(i), h, a).label.display;
                boolean ok = actual.equals(expected[i]);
                if (!ok) {
                    mismatches++;
                }
                System.out.printf(
                        "  %s %s: 代码=%s 表格=%s %s%n",
                        orders.get(i).orderId,
                        orders.get(i).handicapText.isEmpty() || "无".equals(orders.get(i).handicapText)
                                ? orders.get(i).selection
                                : orders.get(i).handicapText + "/" + orders.get(i).selection,
                        actual,
                        expected[i],
                        ok ? "OK" : "MISMATCH");
            }
        }

        System.out.printf("%n抽查不一致数: %d%n", mismatches);

        if (args.length > 0 && "full".equals(args[0])) {
            System.out.println("\n=== 完整矩阵（比分 × 订单）===");
            System.out.print("比分\t");
            for (FootballSportsOrder o : orders) {
                System.out.print(o.orderId.substring(o.orderId.length() - 4) + "\t");
            }
            System.out.println();
            for (var sc : grid.scenarios()) {
                System.out.print(sc.scoreLabel() + "\t");
                for (FootballSportsOrder o : orders) {
                    System.out.print(
                            FootballBetSettlement.settle(o, sc.homeGoals, sc.awayGoals).label.display
                                    + "\t");
                }
                System.out.println();
            }
        }
    }
}
