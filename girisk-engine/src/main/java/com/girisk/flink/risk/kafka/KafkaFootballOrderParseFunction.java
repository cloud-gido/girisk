package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.util.Collector;

/** Kafka 原始字符串 → {@link FootballSportsOrder}，解析失败打日志并跳过。 */
public final class KafkaFootballOrderParseFunction extends RichFlatMapFunction<String, FootballSportsOrder> {
    private static final long serialVersionUID = 1L;

    private final boolean acceptCsv;

    public KafkaFootballOrderParseFunction(boolean acceptCsv) {
        this.acceptCsv = acceptCsv;
    }

    @Override
    public void flatMap(String line, Collector<FootballSportsOrder> out) {
        FootballOrderUnifiedParser.ParseOutcome parsed =
                FootballOrderUnifiedParser.tryParseForRiskPipeline(line, acceptCsv);
        if (parsed.isOk()) {
            out.collect(parsed.order);
        }
    }
}
