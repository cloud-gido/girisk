package com.girisk.flink.risk.config;

import java.io.Serializable;

/** One compact SCOPE_OVERRIDE layer held in BroadcastState. */
public final class ScopeRiskConfigLayer implements Serializable {

    private static final long serialVersionUID = 1L;

    public String scopeType;
    public String scopeKey;
    public long configEpoch;
    public Boolean tradingEnabled;
    public Boolean limitGateEnabled;
    public Boolean exposureGateEnabled;
    public Double delta;
    public Double seedPayoutYuan;
    public Double maxWorstLossYuan;
    public Double maxBetPayoutYuan;

    public ScopeRiskConfigLayer() {}

    public String mapKey() {
        return (scopeType == null ? "OVERALL" : scopeType.trim().toUpperCase())
                + ":"
                + (scopeKey == null || scopeKey.isBlank() ? "_" : scopeKey.trim());
    }
}
