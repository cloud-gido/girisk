package com.girisk.flink.risk.config;

import java.io.Serializable;

/** Resolved duty params for one order (CLI defaults ← layers). */
public final class EffectiveScopeRiskParams implements Serializable {

    private static final long serialVersionUID = 1L;

    public final boolean tradingEnabled;
    public final boolean limitGateEnabled;
    public final boolean exposureGateEnabled;
    public final double limitDelta;
    public final double seedPayoutYuan;
    public final double maxWorstLossYuan;
    /** 单注返彩上限（元）；{@code <= 0} 表示未启用 Gate0。 */
    public final double maxBetPayoutYuan;
    public final String tradingSource;
    public final String limitGateSource;
    public final String exposureGateSource;
    public final String limitsSource;

    public EffectiveScopeRiskParams(
            boolean tradingEnabled,
            boolean limitGateEnabled,
            boolean exposureGateEnabled,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            double maxBetPayoutYuan,
            String tradingSource,
            String limitGateSource,
            String exposureGateSource,
            String limitsSource) {
        this.tradingEnabled = tradingEnabled;
        this.limitGateEnabled = limitGateEnabled;
        this.exposureGateEnabled = exposureGateEnabled;
        this.limitDelta = limitDelta;
        this.seedPayoutYuan = seedPayoutYuan;
        this.maxWorstLossYuan = maxWorstLossYuan;
        this.maxBetPayoutYuan = maxBetPayoutYuan;
        this.tradingSource = tradingSource;
        this.limitGateSource = limitGateSource;
        this.exposureGateSource = exposureGateSource;
        this.limitsSource = limitsSource;
    }

    public static EffectiveScopeRiskParams fromCli(
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            double maxBetPayoutYuan) {
        return new EffectiveScopeRiskParams(
                true,
                true,
                true,
                limitDelta,
                seedPayoutYuan,
                maxWorstLossYuan,
                maxBetPayoutYuan,
                "CLI",
                "CLI",
                "CLI",
                "CLI");
    }
}
