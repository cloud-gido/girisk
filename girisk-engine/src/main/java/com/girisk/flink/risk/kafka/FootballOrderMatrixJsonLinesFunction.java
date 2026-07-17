package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.grid.PerOrderScoreMatrix;
import com.girisk.flink.risk.grid.PerOrderScoreMatrix.ScenarioLine;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.util.Collector;

import java.util.List;

/** 单笔订单 → 网格每格一行 JSON（camelCase 订单字段 + assumedScore、result、platformPayable*）。 */
public final class FootballOrderMatrixJsonLinesFunction extends RichFlatMapFunction<EnrichedFootballOrder, String> {
    private static final long serialVersionUID = 1L;

    private final ScoreGridParams gridParams;

    public FootballOrderMatrixJsonLinesFunction(ScoreGridParams gridParams) {
        this.gridParams = gridParams;
    }

    @Override
    public void flatMap(EnrichedFootballOrder enriched, Collector<String> out) {
        FootballSportsOrder order = enriched.order;
        long publishedAtMs = System.currentTimeMillis();
        List<ScenarioLine> lines = PerOrderScoreMatrix.expand(order, gridParams.grid);
        for (ScenarioLine line : lines) {
            out.collect(FootballOrderKafkaOutcomeJson.orderMatrixRowJson(order, line, publishedAtMs));
        }
    }
}
