package com.girisk.flink.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.flink.risk.grid.LiveScoreGrid;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.kafka.KafkaFootballOrderCsvParser;
import com.girisk.flink.risk.model.LiveMatchScore;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballOrderDetailLiveScoreCoProcessFunctionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SAMPLE_CSV =
            "13883500,FB202605180001,2026-05-18 10:12:00,U1001,模拟英超,星海联,山城竞技,2026-05-20 20:00:00,胜平负,单关,无,主胜,1.86,100";

    @Test
    void scoreShiftChangesDetailAssumedScores() throws Exception {
        var order = KafkaFootballOrderCsvParser.parse(SAMPLE_CSV);
        ScoreGridParams template = ScoreGridParams.fromMap(java.util.Map.of("score", "0:0", "grid", "6"));

        List<String> atZeroZero = emit(order, LiveScoreGrid.resolve(template, new LiveMatchScore("13883500", 0, 0, 0L)));
        List<String> atOneZero = emit(order, LiveScoreGrid.resolve(template, new LiveMatchScore("13883500", 1, 0, 0L)));

        assertEquals(36, atZeroZero.size());
        assertEquals(36, atOneZero.size());
        assertTrue(containsAssumedScore(atZeroZero, "0:0"));
        assertTrue(containsAssumedScore(atOneZero, "1:0"));
        assertTrue(minHomeScore(atOneZero) >= 1);
    }

    private static List<String> emit(
            com.girisk.flink.risk.excel.FootballSportsOrder order, ScoreGridParams grid) {
        List<String> rows = new ArrayList<>();
        FootballOrderDetailLiveScoreCoProcessFunction.emitDetailForOrder(
                order, grid, 100L, new Collector<String>() {
                    @Override
                    public void collect(String record) {
                        rows.add(record);
                    }

                    @Override
                    public void close() {}
                });
        return rows;
    }

    private static boolean containsAssumedScore(List<String> rows, String score) throws Exception {
        for (String json : rows) {
            if (score.equals(MAPPER.readTree(json).get("assumedScore").asText())) {
                return true;
            }
        }
        return false;
    }

    private static int minHomeScore(List<String> rows) throws Exception {
        int min = Integer.MAX_VALUE;
        for (String json : rows) {
            min = Math.min(min, MAPPER.readTree(json).get("homeScore").asInt());
        }
        return min;
    }
}
