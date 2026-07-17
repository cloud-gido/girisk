package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.grid.PerOrderScoreMatrix;
import com.girisk.flink.risk.grid.PerOrderScoreMatrix.ScenarioLine;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.util.Collector;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/** 单笔 Kafka 订单（含事件时间）→ 假设比分网格明细行（文本）。 */
public final class FootballOrderMatrixExpandFunction extends RichFlatMapFunction<EnrichedFootballOrder, String> {
    private static final long serialVersionUID = 1L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter EVENT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZONE);

    private final ScoreGridParams gridParams;
    private transient AtomicLong orderSeq;

    public FootballOrderMatrixExpandFunction(ScoreGridParams gridParams) {
        this.gridParams = gridParams;
    }

    @Override
    public void open(OpenContext openContext) {
        orderSeq = new AtomicLong(0L);
    }

    @Override
    public void flatMap(EnrichedFootballOrder enriched, Collector<String> out) {
        FootballSportsOrder order = enriched.order;
        long seq = orderSeq.incrementAndGet();
        out.collect(
                String.format(
                        Locale.ROOT,
                        "======== 订单 #%d %s | 事件时间=%s | 用户=%s | %s %s %s | 赔率%.2f 金额%d元 | 假设%s（%d条）========",
                        seq,
                        order.orderId,
                        EVENT_FMT.format(Instant.ofEpochMilli(enriched.orderTimeMs)),
                        nullToDash(order.userId),
                        order.playType,
                        nullToDash(order.handicapText),
                        order.selection,
                        order.odds,
                        order.stakeYuan,
                        gridParams.grid.rangeLabel(),
                        gridParams.grid.scenarioCount()));

        List<ScenarioLine> lines = PerOrderScoreMatrix.expand(order, gridParams.grid);
        for (ScenarioLine line : lines) {
            out.collect(PerOrderScoreMatrix.formatScenarioLine(line));
        }
        long max = lines.stream().mapToLong(l -> l.userPnlCents).max().orElse(0L);
        long min = lines.stream().mapToLong(l -> l.userPnlCents).min().orElse(0L);
        out.collect(
                String.format(
                        Locale.ROOT,
                        "[本单汇总] %s 最大盈利=%.2f元 最大亏损=%.2f元",
                        order.orderId,
                        max / 100.0,
                        min / 100.0));
    }

    private static String nullToDash(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }
}
