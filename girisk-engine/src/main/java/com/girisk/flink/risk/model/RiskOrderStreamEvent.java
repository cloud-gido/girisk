package com.girisk.flink.risk.model;

import com.girisk.flink.risk.config.EffectiveScopeRiskParams;

import java.io.Serializable;

/** pre PENDING 试探 或 post 状态回传，统一进入场次敞口算子。 */
public final class RiskOrderStreamEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Kind {
        PRE_PENDING,
        POST_STATUS
    }

    public final Kind kind;
    public final EnrichedFootballOrder prePending;
    public final OrderPostStatusUpdate postUpdate;
    /**
     * 由 {@code girisk.config.v1} enrich 算子填充；null 时下游回退 Job CLI 默认。
     */
    public EffectiveScopeRiskParams scopeParams;

    private RiskOrderStreamEvent(
            Kind kind, EnrichedFootballOrder prePending, OrderPostStatusUpdate postUpdate) {
        this.kind = kind;
        this.prePending = prePending;
        this.postUpdate = postUpdate;
    }

    public static RiskOrderStreamEvent prePending(EnrichedFootballOrder order) {
        return new RiskOrderStreamEvent(Kind.PRE_PENDING, order, null);
    }

    public static RiskOrderStreamEvent postStatus(OrderPostStatusUpdate update) {
        return new RiskOrderStreamEvent(Kind.POST_STATUS, null, update);
    }

    public String fixtureIdForKey() {
        if (kind == Kind.PRE_PENDING) {
            return nz(prePending.order.fixtureId);
        }
        return nz(postUpdate.fixtureId);
    }

    public long eventTimeMs() {
        if (kind == Kind.PRE_PENDING) {
            return prePending.orderTimeMs;
        }
        return postUpdate.eventTimeMs;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
