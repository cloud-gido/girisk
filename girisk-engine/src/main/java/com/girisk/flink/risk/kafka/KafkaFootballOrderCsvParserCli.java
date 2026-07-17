package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.PerOrderScoreMatrix;
import com.girisk.flink.risk.grid.ScoreGridParams;

/**
 * 本地解析一行 Kafka CSV 并打印 6×6 矩阵（不连 Kafka，便于联调）。
 *
 * <pre>
 * java -cp target/classes com.girisk.flink.risk.kafka.KafkaFootballOrderCsvParserCli \
 *   "13883500,FB202605180001,2026-05-18 10:12,U1001,模拟英超,北岸FC,南城竞技,2026-05-20 20:00,胜平负,单关,无,主胜,1.86,100"
 * </pre>
 */
public final class KafkaFootballOrderCsvParserCli {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println(KafkaFootballOrderCsvParser.formatSpec());
            return;
        }
        ScoreGridParams grid = ScoreGridParams.fromArgs(new String[] {"--score", "0:0", "--grid", "6"});
        FootballSportsOrder order = KafkaFootballOrderCsvParser.parse(args[0]);
        System.out.printf("解析成功: %s %s %s 金额%d元%n", order.orderId, order.playType, order.selection, order.stakeYuan);
        PerOrderScoreMatrix.expand(order, grid.grid).forEach(line -> System.out.println(PerOrderScoreMatrix.formatScenarioLine(line)));
    }

    private KafkaFootballOrderCsvParserCli() {}
}
