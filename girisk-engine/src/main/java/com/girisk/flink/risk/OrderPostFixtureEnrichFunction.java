package com.girisk.flink.risk;

import com.girisk.flink.risk.model.EnrichedFootballOrder;
import com.girisk.flink.risk.model.OrderPostStatusUpdate;
import com.girisk.flink.risk.model.RiskOrderStreamEvent;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction;
import org.apache.flink.util.Collector;

import java.util.Locale;

/**
 * 按 orderId 维护 fixtureId 索引，并将 post 回传补齐 fixtureId 后输出统一 {@link RiskOrderStreamEvent}。
 */
public final class OrderPostFixtureEnrichFunction
        extends KeyedCoProcessFunction<String, EnrichedFootballOrder, OrderPostStatusUpdate, RiskOrderStreamEvent> {

    private static final long serialVersionUID = 1L;

    private transient ValueState<String> fixtureIdState;

    @Override
    public void open(OpenContext openContext) {
        fixtureIdState =
                getRuntimeContext().getState(new ValueStateDescriptor<>("order-fixture-id", String.class));
    }

    @Override
    public void processElement1(EnrichedFootballOrder pre, Context ctx, Collector<RiskOrderStreamEvent> out)
            throws Exception {
        if (pre.order.fixtureId != null && !pre.order.fixtureId.isBlank()) {
            fixtureIdState.update(pre.order.fixtureId.trim());
        }
        out.collect(RiskOrderStreamEvent.prePending(pre));
    }

    @Override
    public void processElement2(
            OrderPostStatusUpdate post, Context ctx, Collector<RiskOrderStreamEvent> out) throws Exception {
        String fixtureId = post.fixtureId;
        if (fixtureId == null || fixtureId.isBlank()) {
            fixtureId = fixtureIdState.value();
        }
        if (fixtureId == null || fixtureId.isBlank()) {
            System.err.printf(
                    Locale.ROOT,
                    "[post-enrich] 无法解析 fixtureId，跳过 orderId=%s status=%s%n",
                    post.orderId,
                    post.status);
            return;
        }
        if (post.order != null
                && post.order.fixtureId != null
                && !post.order.fixtureId.isBlank()) {
            fixtureIdState.update(post.order.fixtureId.trim());
        } else if (post.status == com.girisk.flink.risk.model.OrderPostStatus.CONFIRMED) {
            fixtureIdState.update(fixtureId);
        }
        out.collect(RiskOrderStreamEvent.postStatus(post.withFixtureId(fixtureId)));
    }
}
