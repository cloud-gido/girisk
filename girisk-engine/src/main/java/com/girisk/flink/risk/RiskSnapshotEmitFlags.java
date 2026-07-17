package com.girisk.flink.risk;

import java.io.Serializable;

/** Summary / Limit / Business / Decision 下游写出开关。 */
public final class RiskSnapshotEmitFlags implements Serializable {

    private static final long serialVersionUID = 2L;

    public final boolean summary;
    public final boolean limit;
    public final boolean business;
    public final boolean decision;

    public RiskSnapshotEmitFlags(boolean summary, boolean limit, boolean business) {
        this(summary, limit, business, false);
    }

    public RiskSnapshotEmitFlags(boolean summary, boolean limit, boolean business, boolean decision) {
        this.summary = summary;
        this.limit = limit;
        this.business = business;
        this.decision = decision;
    }

    public boolean needsLimitPayload() {
        return limit || business;
    }
}
