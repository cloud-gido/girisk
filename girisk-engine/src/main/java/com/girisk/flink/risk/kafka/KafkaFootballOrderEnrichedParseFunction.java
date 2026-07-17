package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.fixture.FixtureMetadataLookup;
import com.girisk.flink.risk.fixture.FixtureOrderEnricher;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import com.girisk.flink.risk.model.MatchKeys;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;

/**
 * 解析 Kafka 订单（CSV 14 列 / BetConfirmedEvent JSON），可选维表补全赛事字段后输出。
 *
 * <p>维表未命中时不挂起：联赛 / 主客 / 开赛保持空字符串，仍向下游发送。
 */
public final class KafkaFootballOrderEnrichedParseFunction
        extends ProcessFunction<String, EnrichedFootballOrder> {
    private static final long serialVersionUID = 1L;

    private final FixtureMetadataLookup fixtureLookup;
    private final boolean acceptCsv;

    public KafkaFootballOrderEnrichedParseFunction(
            FixtureMetadataLookup fixtureLookup, boolean acceptCsv) {
        this.fixtureLookup = fixtureLookup;
        this.acceptCsv = acceptCsv;
    }

    @Override
    public void processElement(String raw, Context ctx, Collector<EnrichedFootballOrder> out)
            throws Exception {
        FootballOrderUnifiedParser.ParseOutcome parsed =
                FootballOrderUnifiedParser.tryParseForRiskPipeline(raw, acceptCsv);
        if (!parsed.isOk()) {
            return;
        }
        FootballSportsOrder order = parsed.order;
        FixtureOrderEnricher.enrichIfPresent(order, fixtureLookup);
        emit(order, ctx, out);
    }

    private void emit(FootballSportsOrder order, Context ctx, Collector<EnrichedFootballOrder> out) {
        long orderTimeMs =
                OrderEventTimeResolver.resolveMillis(order, ctx.timerService().currentProcessingTime());
        out.collect(new EnrichedFootballOrder(order, orderTimeMs, MatchKeys.of(order)));
    }
}
