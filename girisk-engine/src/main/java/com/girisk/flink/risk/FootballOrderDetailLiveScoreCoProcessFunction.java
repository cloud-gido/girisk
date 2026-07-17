package com.girisk.flink.risk;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.LiveScoreGrid;
import com.girisk.flink.risk.grid.PerOrderScoreMatrix;
import com.girisk.flink.risk.grid.PerOrderScoreMatrix.ScenarioLine;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.kafka.FootballOrderKafkaOutcomeJson;
import com.girisk.flink.risk.kafka.LiveScoreEventParser;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import com.girisk.flink.risk.model.LiveMatchScore;
import com.girisk.flink.risk.time.OrderEventTimes;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction;
import org.apache.flink.util.Collector;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 订单 + 滚球比分双输入：按 fixtureId 累计订单，Detail schema v3 按实时比分动态 6×6 展开。
 *
 * <p>比分更新时若窗口内仍有订单，对该场<strong>全部未清订单</strong>重发 36 行（JSON 格式不变）。
 */
public final class FootballOrderDetailLiveScoreCoProcessFunction
        extends KeyedCoProcessFunction<String, EnrichedFootballOrder, LiveMatchScore, String> {
    private static final long serialVersionUID = 1L;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter EVENT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZONE);

    private final ScoreGridParams gridTemplate;
    private final long cleanupDelayMs;

    private transient ListState<MatchExposureKafkaProcessFunction.StoredOrder> openOrdersState;
    private transient ValueState<Long> lastEventTimeState;
    private transient ValueState<Long> matchCutoffEventTimeState;
    private transient ValueState<Long> cleanupTimerAtState;
    private transient ValueState<LiveMatchScore> liveScoreState;

    public FootballOrderDetailLiveScoreCoProcessFunction(
            ScoreGridParams gridTemplate, long cleanupDelayMs) {
        this.gridTemplate = gridTemplate;
        this.cleanupDelayMs = cleanupDelayMs;
    }

    @Override
    public void open(OpenContext openContext) {
        openOrdersState =
                getRuntimeContext()
                        .getListState(
                                new ListStateDescriptor<>(
                                        "detail-open-orders-by-event-time",
                                        MatchExposureKafkaProcessFunction.StoredOrder.class));
        lastEventTimeState =
                getRuntimeContext().getState(new ValueStateDescriptor<>("detail-last-event-time", Long.class));
        matchCutoffEventTimeState =
                getRuntimeContext()
                        .getState(new ValueStateDescriptor<>("detail-match-cutoff-event-time", Long.class));
        cleanupTimerAtState =
                getRuntimeContext().getState(new ValueStateDescriptor<>("detail-cleanup-timer-at", Long.class));
        liveScoreState =
                getRuntimeContext().getState(new ValueStateDescriptor<>("detail-live-score", LiveMatchScore.class));
    }

    @Override
    public void processElement1(EnrichedFootballOrder value, Context ctx, Collector<String> out)
            throws Exception {
        if (!ensureEventTimeCleanupScheduled(ctx, value)) {
            return;
        }
        Long cutoffMs = matchCutoffEventTimeState.value();
        if (cutoffMs != null && value.orderTimeMs > cutoffMs) {
            logClosed(ctx, value, cutoffMs);
            return;
        }

        Long lastTs = lastEventTimeState.value();
        if (lastTs != null && value.orderTimeMs + 1 < lastTs) {
            logOutOfOrder(ctx, value, lastTs);
        }
        lastEventTimeState.update(Math.max(lastTs == null ? Long.MIN_VALUE : lastTs, value.orderTimeMs));

        List<MatchExposureKafkaProcessFunction.StoredOrder> stored = snapshotOrders();
        String dedupeKey = normalizeOrderId(value.order.orderId);
        boolean duplicate = !dedupeKey.isEmpty() && containsOrderId(stored, dedupeKey);
        if (duplicate) {
            logDuplicate(ctx, value);
            return;
        }

        stored.add(new MatchExposureKafkaProcessFunction.StoredOrder(value.orderTimeMs, value.order));
        stored.sort(Comparator.comparingLong(s -> s.orderTimeMs));
        openOrdersState.update(stored);

        emitDetailForOrder(
                value.order, resolveGrid(), ctx.timerService().currentProcessingTime(), out);
    }

    @Override
    public void processElement2(LiveMatchScore score, Context ctx, Collector<String> out) throws Exception {
        if (score == null || score.fixtureId == null || score.fixtureId.isBlank()) {
            return;
        }
        LiveMatchScore prev = liveScoreState.value();
        liveScoreState.update(score);
        System.out.printf(
                Locale.ROOT,
                "[live-score-detail] fixtureId=%s raw=%s effective=%s phase=%s started=%s ended=%s eventTimeMs=%d%n",
                score.fixtureId,
                prev == null ? "-" : LiveScoreEventParser.formatScore(prev),
                score.formatEffectiveScore(),
                score.formatPhase(),
                score.matchStarted,
                score.matchEnded,
                score.eventTimeMs);

        List<FootballSportsOrder> openOrders = toOrders(snapshotOrders());
        if (openOrders.isEmpty()) {
            return;
        }
        ScoreGridParams grid = resolveGrid();
        long publishedAtMs = ctx.timerService().currentProcessingTime();
        for (FootballSportsOrder order : openOrders) {
            emitDetailForOrder(order, grid, publishedAtMs, out);
        }
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
        Long registeredAt = cleanupTimerAtState.value();
        if (registeredAt == null || timestamp < registeredAt) {
            return;
        }
        int cleared = snapshotOrders().size();
        openOrdersState.clear();
        lastEventTimeState.clear();
        liveScoreState.clear();
        System.err.printf(
                Locale.ROOT,
                "[detail场次状态清理] fixtureId=%s watermark 到达敞口截止=%s 清除订单数=%d%n",
                ctx.getCurrentKey(),
                formatEventTime(timestamp),
                cleared);
    }

    private ScoreGridParams resolveGrid() throws Exception {
        return LiveScoreGrid.resolve(gridTemplate, liveScoreState.value());
    }

    static void emitDetailForOrder(
            FootballSportsOrder order,
            ScoreGridParams grid,
            long publishedAtMs,
            Collector<String> out) {
        List<ScenarioLine> lines = PerOrderScoreMatrix.expand(order, grid.grid);
        for (ScenarioLine line : lines) {
            out.collect(FootballOrderKafkaOutcomeJson.orderMatrixRowJson(order, line, publishedAtMs));
        }
    }

    private boolean ensureEventTimeCleanupScheduled(Context ctx, EnrichedFootballOrder value)
            throws Exception {
        if (cleanupTimerAtState.value() != null) {
            return true;
        }
        String kickoff = value.order.kickoffTime;
        if (kickoff == null || kickoff.isBlank()) {
            return true;
        }
        long kickoffMs;
        try {
            kickoffMs = OrderEventTimes.parseKickoffTimeMillis(kickoff);
        } catch (IllegalArgumentException ex) {
            System.err.printf(
                    Locale.ROOT,
                    "[detail场次状态清理] 无法解析开赛时间 fixtureId=%s kickoff=%s%n",
                    value.order.fixtureId,
                    kickoff);
            return true;
        }
        long cutoffMs = kickoffMs + cleanupDelayMs;
        matchCutoffEventTimeState.update(cutoffMs);
        ctx.timerService().registerEventTimeTimer(cutoffMs);
        cleanupTimerAtState.update(cutoffMs);
        return true;
    }

    private List<MatchExposureKafkaProcessFunction.StoredOrder> snapshotOrders() throws Exception {
        List<MatchExposureKafkaProcessFunction.StoredOrder> list = new ArrayList<>();
        for (MatchExposureKafkaProcessFunction.StoredOrder s : openOrdersState.get()) {
            list.add(s);
        }
        return list;
    }

    private static List<FootballSportsOrder> toOrders(
            List<MatchExposureKafkaProcessFunction.StoredOrder> stored) {
        List<FootballSportsOrder> orders = new ArrayList<>(stored.size());
        for (MatchExposureKafkaProcessFunction.StoredOrder s : stored) {
            orders.add(s.order);
        }
        return orders;
    }

    private static String normalizeOrderId(String orderId) {
        return orderId == null ? "" : orderId.trim();
    }

    private static boolean containsOrderId(
            List<MatchExposureKafkaProcessFunction.StoredOrder> stored, String dedupeKey) {
        for (MatchExposureKafkaProcessFunction.StoredOrder s : stored) {
            if (dedupeKey.equals(normalizeOrderId(s.order.orderId))) {
                return true;
            }
        }
        return false;
    }

    private static String formatEventTime(long epochMs) {
        return EVENT_FMT.format(Instant.ofEpochMilli(epochMs));
    }

    private static void logClosed(Context ctx, EnrichedFootballOrder value, long cutoffMs) {
        System.err.printf(
                Locale.ROOT,
                "[detail场次已关闭] fixtureId=%s orderId=%s 下单=%s 晚于截止=%s%n",
                ctx.getCurrentKey(),
                value.order.orderId,
                formatEventTime(value.orderTimeMs),
                formatEventTime(cutoffMs));
    }

    private static void logOutOfOrder(Context ctx, EnrichedFootballOrder value, Long lastTs) {
        System.err.printf(
                Locale.ROOT,
                "[detail事件时间乱序] fixtureId=%s 当前=%s 状态最后=%s orderId=%s%n",
                ctx.getCurrentKey(),
                formatEventTime(value.orderTimeMs),
                formatEventTime(lastTs),
                value.order.orderId);
    }

    private static void logDuplicate(Context ctx, EnrichedFootballOrder value) {
        System.err.printf(
                Locale.ROOT,
                "[detail订单去重] fixtureId=%s 忽略重复 orderId=%s%n",
                ctx.getCurrentKey(),
                value.order.orderId);
    }
}
