package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.model.LiveMatchScore;
import org.apache.flink.api.common.functions.RichMapFunction;

/** keyBy fixtureId 前规范化。 */
public final class LiveScoreFixtureKeyFunction extends RichMapFunction<LiveMatchScore, LiveMatchScore> {
    private static final long serialVersionUID = 1L;

    @Override
    public LiveMatchScore map(LiveMatchScore score) {
        score.fixtureId = LiveScoreEventParser.fixtureKey(score);
        return score;
    }
}
