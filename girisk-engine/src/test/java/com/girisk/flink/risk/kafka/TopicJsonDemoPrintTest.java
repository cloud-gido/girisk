package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** 打印 Summary / Limit 示例 JSON（{@code mvn test -Dtest=TopicJsonDemoPrintTest}）。 */
class TopicJsonDemoPrintTest {

    @Test
    void printSummaryAndLimitDemos() {
        FootballSportsOrder trigger =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O3,2026-05-18 10:12:00,U1001,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,主胜,1.85,100");
        trigger.operatorId = 1001L;
        trigger.eventId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

        FootballSportsOrder o2 =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O2,2026-05-18 10:10:00,U1002,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,平局,3.20,50");
        FootballSportsOrder o1 =
                KafkaFootballOrderCsvParser.parse(
                        "13883500,O1,2026-05-18 10:08:00,U1003,英超,曼城,利物浦,2026-05-16 22:00:00,胜平负,单关,无,客胜,4.50,50");

        String matchKey = "13883500|英超|曼城|利物浦|2026-05-16 22:00:00";
        EnrichedFootballOrder enriched = new EnrichedFootballOrder(trigger, 1779077520000L, matchKey);
        ScoreGridParams grid = ScoreGridParams.fromMap(Map.of("score", "0:0", "grid", "2"));
        var exposure = MatchExposureAggregator.summarize(List.of(o1, o2, trigger), grid.grid);
        String summary =
                MatchExposureSummaryJson.summarySnapshotJson(
                        enriched, matchKey, false, false, false, 3, exposure, grid, 1779077520100L);
        String limit =
                MatchLimitSummaryJson.limitSnapshotJson(
                        enriched,
                        matchKey,
                        3,
                        false,
                        0.2,
                        2000.0,
                        List.of(o1, o2),
                        List.of(o1, o2, trigger),
                        "NONE",
                        323.1,
                        1000.0,
                        false,
                        1779077520100L);

        System.out.println("===== SUMMARY TOPIC (girisk.football.summary.result) =====");
        System.out.println(summary);
        System.out.println();
        System.out.println("===== LIMIT TOPIC (girisk.football.limit.result) =====");
        System.out.println(limit);
    }
}
