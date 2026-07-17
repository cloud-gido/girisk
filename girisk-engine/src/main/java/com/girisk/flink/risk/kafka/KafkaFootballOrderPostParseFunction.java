package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.model.OrderPostStatusUpdate;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;

/** 解析 post topic 状态回传。 */
public final class KafkaFootballOrderPostParseFunction
        extends ProcessFunction<String, OrderPostStatusUpdate> {
    private static final long serialVersionUID = 1L;

    @Override
    public void processElement(String raw, Context ctx, Collector<OrderPostStatusUpdate> out) {
        FootballOrderUnifiedParser.PostParseOutcome parsed =
                FootballOrderUnifiedParser.tryParsePostStatus(raw);
        if (parsed.isOk()) {
            out.collect(parsed.update);
        }
    }
}
