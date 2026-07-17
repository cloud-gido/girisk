package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.model.LiveMatchScore;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.util.Collector;

/** 滚球比分 Kafka 行 → {@link LiveMatchScore}，非法行跳过。 */
public final class LiveScoreKafkaParseFunction extends RichFlatMapFunction<String, LiveMatchScore> {
    private static final long serialVersionUID = 1L;

    @Override
    public void flatMap(String raw, Collector<LiveMatchScore> out) {
        try {
            LiveMatchScore score = LiveScoreEventParser.parse(raw);
            if (score.fixtureId != null && !score.fixtureId.isBlank()) {
                out.collect(score);
            }
        } catch (IllegalArgumentException ex) {
            System.err.printf("[parse-live-score] 跳过: %s%n", ex.getMessage());
        }
    }
}
