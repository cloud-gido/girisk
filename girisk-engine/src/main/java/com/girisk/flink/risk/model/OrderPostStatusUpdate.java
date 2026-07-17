package com.girisk.flink.risk.model;

import com.girisk.flink.risk.excel.FootballSportsOrder;

import java.io.Serializable;

/** post topic 状态回传（CONFIRMED 可带完整订单；REJECTED/CASHED_OUT 可能无 legs）。 */
public final class OrderPostStatusUpdate implements Serializable {

    private static final long serialVersionUID = 1L;

    public final OrderPostStatus status;
    public final String orderId;
    public final String fixtureId;
    public final long eventTimeMs;
    /** CONFIRMED 且 legs 非空时有值；REJECTED 无 legs 时为 null。 */
    public final FootballSportsOrder order;

    public OrderPostStatusUpdate(
            OrderPostStatus status,
            String orderId,
            String fixtureId,
            long eventTimeMs,
            FootballSportsOrder order) {
        this.status = status;
        this.orderId = orderId == null ? "" : orderId.trim();
        this.fixtureId = fixtureId == null ? "" : fixtureId.trim();
        this.eventTimeMs = eventTimeMs;
        this.order = order;
    }

    public OrderPostStatusUpdate withFixtureId(String fixtureId) {
        if (fixtureId == null || fixtureId.isBlank()) {
            return this;
        }
        return new OrderPostStatusUpdate(status, orderId, fixtureId, eventTimeMs, order);
    }
}
