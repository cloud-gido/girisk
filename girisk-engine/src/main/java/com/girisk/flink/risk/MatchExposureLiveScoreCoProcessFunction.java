package com.girisk.flink.risk;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.LiveScoreGrid;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.kafka.LiveScoreEventParser;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import com.girisk.flink.risk.model.OrderPostStatusUpdate;
import com.girisk.flink.risk.model.RiskOrderStreamEvent;
import com.girisk.flink.risk.model.LiveMatchScore;
import com.girisk.flink.risk.limit.MarketStakeAggregator;
import com.girisk.flink.risk.limit.MatchTriggerAcceptance;
import com.girisk.flink.risk.model.MatchKeys;
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
 * 订单 + 滚球比分双输入：按 fixtureId 累计订单，用实时比分动态 6×6 网格输出 Summary v7 / Limit v1。
 *
 * <p>比分更新时若窗口内仍有订单，会重发 Summary（trigger 元数据沿用最近一次订单，JSON 格式不变）。
 */
public final class MatchExposureLiveScoreCoProcessFunction
        extends KeyedCoProcessFunction<String, RiskOrderStreamEvent, LiveMatchScore, String> {
    private static final long serialVersionUID = 1L;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter EVENT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZONE);

    private final ScoreGridParams gridTemplate;
    private final long cleanupDelayMs;
    private final double limitDelta;
    private final double seedPayoutYuan;
    private final double maxWorstLossYuan;
    private final RiskSnapshotEmitFlags emitFlags;
    private final boolean postFeedbackEnabled;
    private final long pendingReserveTtlMs;

    private transient ListState<MatchExposureKafkaProcessFunction.StoredOrder> openOrdersState;
    private transient ValueState<Long> lastEventTimeState;
    private transient ValueState<Long> matchCutoffEventTimeState;
    private transient ValueState<Long> cleanupTimerAtState;
    private transient ValueState<LiveMatchScore> liveScoreState;
    private transient ValueState<EnrichedFootballOrder> lastTriggerOrderState;
    private transient ValueState<PendingReserveBook> pendingBookState;

    public MatchExposureLiveScoreCoProcessFunction(
            ScoreGridParams gridTemplate,
            long cleanupDelayMs,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            RiskSnapshotEmitFlags emitFlags,
            boolean postFeedbackEnabled) {
        this(
                gridTemplate,
                cleanupDelayMs,
                limitDelta,
                seedPayoutYuan,
                maxWorstLossYuan,
                emitFlags,
                postFeedbackEnabled,
                30_000L);
    }

    public MatchExposureLiveScoreCoProcessFunction(
            ScoreGridParams gridTemplate,
            long cleanupDelayMs,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            RiskSnapshotEmitFlags emitFlags,
            boolean postFeedbackEnabled,
            long pendingReserveTtlMs) {
        this.gridTemplate = gridTemplate;
        this.cleanupDelayMs = cleanupDelayMs;
        this.limitDelta = limitDelta;
        this.seedPayoutYuan = seedPayoutYuan;
        this.maxWorstLossYuan = maxWorstLossYuan;
        this.emitFlags = emitFlags;
        this.postFeedbackEnabled = postFeedbackEnabled;
        this.pendingReserveTtlMs = pendingReserveTtlMs;
    }

    @Override
    public void open(OpenContext openContext) {
        openOrdersState =
                getRuntimeContext()
                        .getListState(
                                new ListStateDescriptor<>(
                                        "open-orders-by-event-time",
                                        MatchExposureKafkaProcessFunction.StoredOrder.class));
        lastEventTimeState =
                getRuntimeContext().getState(new ValueStateDescriptor<>("last-event-time", Long.class));
        matchCutoffEventTimeState =
                getRuntimeContext().getState(new ValueStateDescriptor<>("match-cutoff-event-time", Long.class));
        cleanupTimerAtState =
                getRuntimeContext().getState(new ValueStateDescriptor<>("cleanup-timer-at", Long.class));
        liveScoreState =
                getRuntimeContext().getState(new ValueStateDescriptor<>("live-score", LiveMatchScore.class));
        lastTriggerOrderState =
                getRuntimeContext()
                        .getState(new ValueStateDescriptor<>("last-trigger-order", EnrichedFootballOrder.class));
        pendingBookState =
                getRuntimeContext()
                        .getState(new ValueStateDescriptor<>("pending-reserve-book", PendingReserveBook.class));
    }

    @Override
    public void processElement1(RiskOrderStreamEvent event, Context ctx, Collector<String> out)
            throws Exception {
        if (event.kind == RiskOrderStreamEvent.Kind.POST_STATUS) {
            if (postFeedbackEnabled) {
                applyPostUpdate(event.postUpdate);
            }
            return;
        }
        processPrePending(event.prePending, ctx, out);
    }

    private void applyPostUpdate(OrderPostStatusUpdate update) throws Exception {
        List<MatchExposureKafkaProcessFunction.StoredOrder> stored = snapshotOrders();
        ConfirmedOrderWindowState.applyPostUpdate(stored, update);
        openOrdersState.update(stored);
        PendingReserveBook book = pendingBookOrEmpty();
        book.remove(update.orderId);
        pendingBookState.update(book);
    }

    private void processPrePending(EnrichedFootballOrder value, Context ctx, Collector<String> out)
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
        boolean eventTimeOutOfOrder = lastTs != null && value.orderTimeMs + 1 < lastTs;
        if (eventTimeOutOfOrder) {
            logOutOfOrder(ctx, value, lastTs);
        }
        lastEventTimeState.update(Math.max(lastTs == null ? Long.MIN_VALUE : lastTs, value.orderTimeMs));

        List<MatchExposureKafkaProcessFunction.StoredOrder> stored = snapshotOrders();
        String dedupeKey = ConfirmedOrderWindowState.normalizeOrderId(value.order.orderId);
        boolean duplicate = !dedupeKey.isEmpty() && ConfirmedOrderWindowState.containsOrderId(stored, dedupeKey);
        if (duplicate) {
            logDuplicate(ctx, value);
        }

        long nowMs = ctx.timerService().currentProcessingTime();
        PendingReserveBook book = pendingBookOrEmpty();
        book.purgeExpired(nowMs);
        List<FootballSportsOrder> priorAccepted = ConfirmedOrderWindowState.toOrders(stored);
        if (postFeedbackEnabled) {
            priorAccepted = new ArrayList<>(priorAccepted);
            priorAccepted.addAll(book.toOrders(nowMs));
        }
        ScoreGridParams grid = LiveScoreGrid.resolve(gridTemplate, liveScoreState.value());
        MatchTriggerAcceptance acceptance =
                MatchTriggerAcceptance.evaluate(
                        priorAccepted,
                        value,
                        duplicate,
                        grid.grid,
                        limitDelta,
                        seedPayoutYuan,
                        maxWorstLossYuan,
                        postFeedbackEnabled);

        if (!postFeedbackEnabled && acceptance.persistTrigger()) {
            stored.add(new MatchExposureKafkaProcessFunction.StoredOrder(value.orderTimeMs, value.order));
            stored.sort(Comparator.comparingLong(s -> s.orderTimeMs));
            openOrdersState.update(stored);
        } else if (postFeedbackEnabled && !duplicate && !acceptance.triggerRejected) {
            long expireAt = nowMs + pendingReserveTtlMs;
            book.reserve(value.order, expireAt);
            pendingBookState.update(book);
            ctx.timerService().registerProcessingTimeTimer(expireAt);
        } else if (acceptance.triggerRejected) {
            System.err.printf(
                    Locale.ROOT,
                    "[建议拒单:%s] fixtureId=%s 不纳入敞口 orderId=%s stakeYuan=%d%n",
                    acceptance.rejectReason,
                    ctx.getCurrentKey(),
                    value.order.orderId,
                    value.order.stakeYuan);
        }

        lastTriggerOrderState.update(value);
        emitSnapshot(value, MatchKeys.of(value.order), acceptance, eventTimeOutOfOrder, ctx, out);
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
                "[live-score] fixtureId=%s raw=%s effective=%s phase=%s started=%s ended=%s eventTimeMs=%d%n",
                score.fixtureId,
                prev == null ? "-" : LiveScoreEventParser.formatScore(prev),
                score.formatEffectiveScore(),
                score.formatPhase(),
                score.matchStarted,
                score.matchEnded,
                score.eventTimeMs);

        EnrichedFootballOrder trigger = lastTriggerOrderState.value();
        List<MatchExposureKafkaProcessFunction.StoredOrder> stored = snapshotOrders();
        List<FootballSportsOrder> openOrders = ConfirmedOrderWindowState.toOrders(stored);
        if (trigger == null || openOrders.isEmpty()) {
            return;
        }
        ScoreGridParams grid = LiveScoreGrid.resolve(gridTemplate, liveScoreState.value());
        boolean triggerAccepted =
                ConfirmedOrderWindowState.containsOrderId(
                        stored, ConfirmedOrderWindowState.normalizeOrderId(trigger.order.orderId));
        List<FootballSportsOrder> prior =
                triggerAccepted
                        ? MarketStakeAggregator.excludeOrderId(
                                openOrders, trigger.order.orderId)
                        : openOrders;
        MatchTriggerAcceptance acceptance =
                MatchTriggerAcceptance.evaluate(
                        prior,
                        trigger,
                        triggerAccepted,
                        grid.grid,
                        limitDelta,
                        seedPayoutYuan,
                        maxWorstLossYuan,
                        postFeedbackEnabled);
        emitSnapshot(trigger, MatchKeys.of(trigger.order), acceptance, false, ctx, out);
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
        PendingReserveBook book = pendingBookOrEmpty();
        if (book.purgeExpired(timestamp) > 0) {
            pendingBookState.update(book);
        }
        Long registeredAt = cleanupTimerAtState.value();
        if (registeredAt == null || timestamp < registeredAt) {
            return;
        }
        int cleared = snapshotOrders().size();
        openOrdersState.clear();
        lastEventTimeState.clear();
        lastTriggerOrderState.clear();
        liveScoreState.clear();
        pendingBookState.clear();
        System.err.printf(
                Locale.ROOT,
                "[场次状态清理] fixtureId=%s watermark 到达敞口截止=%s 清除订单数=%d%n",
                ctx.getCurrentKey(),
                formatEventTime(timestamp),
                cleared);
    }

    private PendingReserveBook pendingBookOrEmpty() throws Exception {
        PendingReserveBook book = pendingBookState.value();
        return book == null ? new PendingReserveBook() : book;
    }

    private void emitSnapshot(
            EnrichedFootballOrder trigger,
            String matchKey,
            MatchTriggerAcceptance acceptance,
            boolean eventTimeOutOfOrder,
            Context ctx,
            Collector<String> out)
            throws Exception {
        ScoreGridParams grid = LiveScoreGrid.resolve(gridTemplate, liveScoreState.value());
        long publishedAtMs = ctx.timerService().currentProcessingTime();
        MatchExposureSnapshotEmitter.emit(
                trigger,
                matchKey,
                acceptance,
                grid,
                eventTimeOutOfOrder,
                publishedAtMs,
                limitDelta,
                seedPayoutYuan,
                maxWorstLossYuan,
                emitFlags.summary,
                emitFlags.limit,
                emitFlags.business,
                emitFlags.decision,
                out,
                MatchExposureKafkaProcessFunction.LIMIT_SNAPSHOT_TAG,
                MatchExposureKafkaProcessFunction.BUSINESS_SNAPSHOT_TAG,
                MatchExposureKafkaProcessFunction.DECISION_TAG,
                sideOutputCtx(ctx));
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
                    "[场次状态清理] 无法解析开赛时间 fixtureId=%s kickoff=%s%n",
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

    private static String formatEventTime(long epochMs) {
        return EVENT_FMT.format(Instant.ofEpochMilli(epochMs));
    }

    private static void logClosed(Context ctx, EnrichedFootballOrder value, long cutoffMs) {
        System.err.printf(
                Locale.ROOT,
                "[场次已关闭] fixtureId=%s orderId=%s 下单=%s 晚于截止=%s%n",
                ctx.getCurrentKey(),
                value.order.orderId,
                formatEventTime(value.orderTimeMs),
                formatEventTime(cutoffMs));
    }

    private static void logOutOfOrder(Context ctx, EnrichedFootballOrder value, Long lastTs) {
        System.err.printf(
                Locale.ROOT,
                "[事件时间乱序] fixtureId=%s 当前=%s 状态最后=%s orderId=%s%n",
                ctx.getCurrentKey(),
                formatEventTime(value.orderTimeMs),
                formatEventTime(lastTs),
                value.order.orderId);
    }

    private static void logDuplicate(Context ctx, EnrichedFootballOrder value) {
        System.err.printf(
                Locale.ROOT,
                "[订单去重] fixtureId=%s 忽略重复 orderId=%s%n",
                ctx.getCurrentKey(),
                value.order.orderId);
    }

    private static MatchExposureSnapshotEmitter.SideOutputContext sideOutputCtx(Context ctx) {
        return new MatchExposureSnapshotEmitter.SideOutputContext() {
            @Override
            public <X> void output(org.apache.flink.util.OutputTag<X> outputTag, X value) {
                ctx.output(outputTag, value);
            }
        };
    }
}
