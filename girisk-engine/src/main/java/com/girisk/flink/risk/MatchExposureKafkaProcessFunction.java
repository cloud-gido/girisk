package com.girisk.flink.risk;

import com.girisk.flink.risk.config.EffectiveScopeRiskParams;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.ScoreGridParams;
import com.girisk.flink.risk.limit.ExposureLimitGate;
import com.girisk.flink.risk.limit.MatchTriggerAcceptance;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import com.girisk.flink.risk.model.OrderPostStatusUpdate;
import com.girisk.flink.risk.model.RiskOrderStreamEvent;
import com.girisk.flink.risk.time.OrderEventTimes;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.util.OutputTag;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 按场次窗口汇总假设比分敞口，每次触发写一条 schemaVersion=7 嵌套 JSON（全网格 assumedScores）。
 *
 * <p>订单累计与场次清理均使用<strong>事件时间</strong>（CSV 下单时间 / watermark）：当 watermark 到达「开赛 +
 * {@link #cleanupDelayMs}」时清空 ListState；下单事件时间晚于该时刻的订单不再参与汇总。
 */
public final class MatchExposureKafkaProcessFunction
        extends KeyedProcessFunction<String, RiskOrderStreamEvent, String> {
    private static final long serialVersionUID = 1L;

    /** 等比例限额快照（与主输出 summary 同触发，写入 --sink.topic.limit）。 */
    public static final OutputTag<String> LIMIT_SNAPSHOT_TAG =
            new OutputTag<>("match-limit-snapshot") {};

    /** 业务方汇总快照（写入 --sink.topic.business）。 */
    public static final OutputTag<String> BUSINESS_SNAPSHOT_TAG =
            new OutputTag<>("match-business-snapshot") {};

    /** 统一决策出口（写入 --sink.topic.decision / girisk.decision.v1）。 */
    public static final OutputTag<String> DECISION_TAG =
            new OutputTag<>("risk-decision-v1") {};
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter EVENT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZONE);

    private final ScoreGridParams gridParams;
    private final long cleanupDelayMs;
    private final double limitDelta;
    private final double seedPayoutYuan;
    private final double maxWorstLossYuan;
    private final RiskSnapshotEmitFlags emitFlags;
    private final boolean postFeedbackEnabled;

    private transient ListState<StoredOrder> openOrdersState;
    /** 本场全部已见订单（含拒单），用于「完全不拦截」对照敞口。 */
    private transient ListState<StoredOrder> allSeenOrdersState;
    private transient ValueState<Long> lastEventTimeState;
    /** 场次敞口截止事件时间（开赛 + cleanupDelayMs，epoch ms）。 */
    private transient ValueState<Long> matchCutoffEventTimeState;
    /** 已注册的事件时间清理定时器触发时刻，用于 onTimer 去重。 */
    private transient ValueState<Long> cleanupTimerAtState;

    public MatchExposureKafkaProcessFunction(ScoreGridParams gridParams) {
        this(
                gridParams,
                3L * 60L * 60L * 1000L,
                0.2,
                2000.0,
                ExposureLimitGate.WORST_LOSS_DISABLED,
                new RiskSnapshotEmitFlags(true, true, false),
                false);
    }

    public MatchExposureKafkaProcessFunction(ScoreGridParams gridParams, long cleanupDelayMs) {
        this(
                gridParams,
                cleanupDelayMs,
                0.2,
                2000.0,
                ExposureLimitGate.WORST_LOSS_DISABLED,
                new RiskSnapshotEmitFlags(true, true, false),
                false);
    }

    public MatchExposureKafkaProcessFunction(
            ScoreGridParams gridParams,
            long cleanupDelayMs,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            RiskSnapshotEmitFlags emitFlags,
            boolean postFeedbackEnabled) {
        this.gridParams = gridParams;
        this.cleanupDelayMs = cleanupDelayMs;
        this.limitDelta = limitDelta;
        this.seedPayoutYuan = seedPayoutYuan;
        this.maxWorstLossYuan = maxWorstLossYuan;
        this.emitFlags = emitFlags;
        this.postFeedbackEnabled = postFeedbackEnabled;
    }

    @Override
    public void open(OpenContext openContext) {
        openOrdersState =
                getRuntimeContext()
                        .getListState(new ListStateDescriptor<>("open-orders-by-event-time", StoredOrder.class));
        allSeenOrdersState =
                getRuntimeContext()
                        .getListState(new ListStateDescriptor<>("all-seen-orders-by-event-time", StoredOrder.class));
        lastEventTimeState =
                getRuntimeContext().getState(new ValueStateDescriptor<>("last-event-time", Long.class));
        matchCutoffEventTimeState =
                getRuntimeContext().getState(new ValueStateDescriptor<>("match-cutoff-event-time", Long.class));
        cleanupTimerAtState =
                getRuntimeContext().getState(new ValueStateDescriptor<>("cleanup-timer-at", Long.class));
    }

    @Override
    public void processElement(RiskOrderStreamEvent event, Context ctx, Collector<String> out)
            throws Exception {
        if (event.kind == RiskOrderStreamEvent.Kind.POST_STATUS) {
            if (postFeedbackEnabled) {
                applyPostUpdate(event.postUpdate, ctx);
            }
            return;
        }
        processPrePending(event.prePending, event.scopeParams, ctx, out);
    }

    private void applyPostUpdate(OrderPostStatusUpdate update, Context ctx) throws Exception {
        List<StoredOrder> stored = snapshotOrders();
        ConfirmedOrderWindowState.applyPostUpdate(stored, update);
        openOrdersState.update(stored);
    }

    private void processPrePending(
            EnrichedFootballOrder value,
            EffectiveScopeRiskParams scopeParams,
            Context ctx,
            Collector<String> out)
            throws Exception {
        if (!ensureEventTimeCleanupScheduled(ctx, value)) {
            return;
        }

        Long cutoffMs = matchCutoffEventTimeState.value();
        if (cutoffMs != null && value.orderTimeMs > cutoffMs) {
            System.err.printf(
                    Locale.ROOT,
                    "[场次已关闭] 忽略订单 场次=%s orderId=%s 下单事件时间=%s 晚于敞口截止=%s（开赛后 %d 小时，事件时间语义）%n",
                    ctx.getCurrentKey(),
                    value.order.orderId,
                    formatEventTime(value.orderTimeMs),
                    formatEventTime(cutoffMs),
                    cleanupDelayMs / 3_600_000L);
            return;
        }

        Long lastTs = lastEventTimeState.value();
        boolean eventTimeOutOfOrder = lastTs != null && value.orderTimeMs + 1 < lastTs;
        if (eventTimeOutOfOrder) {
            System.err.printf(
                    Locale.ROOT,
                    "[事件时间乱序] 场次=%s 当前=%s 早于状态最后=%s 订单=%s%n",
                    ctx.getCurrentKey(),
                    formatEventTime(value.orderTimeMs),
                    formatEventTime(lastTs),
                    value.order.orderId);
        }
        lastEventTimeState.update(Math.max(lastTs == null ? Long.MIN_VALUE : lastTs, value.orderTimeMs));

        List<StoredOrder> stored = snapshotOrders();
        List<StoredOrder> allSeen = snapshotAllSeen();
        String dedupeKey = ConfirmedOrderWindowState.normalizeOrderId(value.order.orderId);
        boolean duplicate = !dedupeKey.isEmpty() && ConfirmedOrderWindowState.containsOrderId(stored, dedupeKey);
        if (duplicate) {
            System.err.printf(
                    Locale.ROOT,
                    "[订单去重] 场次=%s 忽略重复 orderId=%s 事件时间=%s%n",
                    ctx.getCurrentKey(),
                    value.order.orderId,
                    formatEventTime(value.orderTimeMs));
        }

        List<FootballSportsOrder> priorAccepted = ConfirmedOrderWindowState.toOrders(stored);
        double effDelta = scopeParams != null ? scopeParams.limitDelta : limitDelta;
        double effSeed = scopeParams != null ? scopeParams.seedPayoutYuan : seedPayoutYuan;
        double effMaxWorst = scopeParams != null ? scopeParams.maxWorstLossYuan : maxWorstLossYuan;
        double effMaxBet = scopeParams != null ? scopeParams.maxBetPayoutYuan : 0.0;
        boolean tradingOn = scopeParams == null || scopeParams.tradingEnabled;
        boolean limitOn = scopeParams == null || scopeParams.limitGateEnabled;
        boolean exposureOn = scopeParams == null || scopeParams.exposureGateEnabled;
        MatchTriggerAcceptance acceptance =
                MatchTriggerAcceptance.evaluate(
                        priorAccepted,
                        value,
                        duplicate,
                        gridParams.grid,
                        effDelta,
                        effSeed,
                        effMaxWorst,
                        effMaxBet,
                        tradingOn,
                        limitOn,
                        exposureOn,
                        postFeedbackEnabled);

        if (!postFeedbackEnabled && acceptance.persistTrigger()) {
            stored.add(new StoredOrder(value.orderTimeMs, value.order));
            stored.sort(Comparator.comparingLong(s -> s.orderTimeMs));
            openOrdersState.update(stored);
        } else if (acceptance.triggerRejected) {
            System.err.printf(
                    Locale.ROOT,
                    "[建议拒单:%s] 场次=%s 不纳入敞口 orderId=%s stakeYuan=%d%n",
                    acceptance.rejectReason,
                    ctx.getCurrentKey(),
                    value.order.orderId,
                    value.order.stakeYuan);
        }
        // 全量对照：接单/拒单都记入（按 orderId 去重），与离线「完全不拦截」一致
        if (!duplicate
                && !dedupeKey.isEmpty()
                && !ConfirmedOrderWindowState.containsOrderId(allSeen, dedupeKey)) {
            allSeen.add(new StoredOrder(value.orderTimeMs, value.order));
            allSeen.sort(Comparator.comparingLong(s -> s.orderTimeMs));
            allSeenOrdersState.update(allSeen);
        }

        String matchKey = ctx.getCurrentKey();
        long publishedAtMs = ctx.timerService().currentProcessingTime();
        MatchExposureSnapshotEmitter.emit(
                value,
                matchKey,
                acceptance,
                ConfirmedOrderWindowState.toOrders(allSeen),
                gridParams,
                eventTimeOutOfOrder,
                publishedAtMs,
                effDelta,
                effSeed,
                effMaxWorst,
                emitFlags.summary,
                emitFlags.limit,
                emitFlags.business,
                emitFlags.decision,
                out,
                LIMIT_SNAPSHOT_TAG,
                BUSINESS_SNAPSHOT_TAG,
                DECISION_TAG,
                sideOutputCtx(ctx));
    }

    private static MatchExposureSnapshotEmitter.SideOutputContext sideOutputCtx(Context ctx) {
        return new MatchExposureSnapshotEmitter.SideOutputContext() {
            @Override
            public <X> void output(org.apache.flink.util.OutputTag<X> outputTag, X value) {
                ctx.output(outputTag, value);
            }
        };
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
        Long registeredAt = cleanupTimerAtState.value();
        if (registeredAt == null || timestamp < registeredAt) {
            return;
        }
        int cleared = snapshotOrders().size();
        openOrdersState.clear();
        allSeenOrdersState.clear();
        lastEventTimeState.clear();
        System.err.printf(
                Locale.ROOT,
                "[场次状态清理] 场次=%s watermark 到达敞口截止事件时间=%s 清除订单数=%d（开赛后 %d 小时）%n",
                ctx.getCurrentKey(),
                formatEventTime(timestamp),
                cleared,
                cleanupDelayMs / 3_600_000L);
    }

    /**
     * 解析开赛时间，注册事件时间清理定时器（仅一次）。返回 false 表示无法解析开赛时间，本单跳过。
     */
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
                    "[场次状态清理] 无法解析开赛时间，跳过清理定时器 场次=%s kickoff=%s 原因=%s%n",
                    ctx.getCurrentKey(),
                    kickoff,
                    ex.getMessage());
            return true;
        }
        long cutoffMs = kickoffMs + cleanupDelayMs;
        matchCutoffEventTimeState.update(cutoffMs);
        ctx.timerService().registerEventTimeTimer(cutoffMs);
        cleanupTimerAtState.update(cutoffMs);
        return true;
    }

    private List<StoredOrder> snapshotOrders() throws Exception {
        List<StoredOrder> list = new ArrayList<>();
        for (StoredOrder s : openOrdersState.get()) {
            list.add(s);
        }
        return list;
    }

    private List<StoredOrder> snapshotAllSeen() throws Exception {
        List<StoredOrder> list = new ArrayList<>();
        for (StoredOrder s : allSeenOrdersState.get()) {
            list.add(s);
        }
        return list;
    }

    private static String formatEventTime(long epochMs) {
        return EVENT_FMT.format(Instant.ofEpochMilli(epochMs));
    }

    public static final class StoredOrder implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public long orderTimeMs;
        public FootballSportsOrder order;

        public StoredOrder() {}

        public StoredOrder(long orderTimeMs, FootballSportsOrder order) {
            this.orderTimeMs = orderTimeMs;
            this.order = order;
        }
    }
}
