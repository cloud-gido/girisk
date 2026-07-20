package com.girisk.flink.risk.config;

import java.io.Serializable;
import java.util.Map;

/**
 * Resolve Match → League → Sport → Overall → CLI defaults.
 */
public final class ScopeRiskConfigResolver implements Serializable {

    private static final long serialVersionUID = 1L;

    private final double defaultDelta;
    private final double defaultSeedPayoutYuan;
    private final double defaultMaxWorstLossYuan;
    private final double defaultMaxBetPayoutYuan;

    public ScopeRiskConfigResolver(
            double defaultDelta, double defaultSeedPayoutYuan, double defaultMaxWorstLossYuan) {
        this(defaultDelta, defaultSeedPayoutYuan, defaultMaxWorstLossYuan, 0.0);
    }

    public ScopeRiskConfigResolver(
            double defaultDelta,
            double defaultSeedPayoutYuan,
            double defaultMaxWorstLossYuan,
            double defaultMaxBetPayoutYuan) {
        this.defaultDelta = defaultDelta;
        this.defaultSeedPayoutYuan = defaultSeedPayoutYuan;
        this.defaultMaxWorstLossYuan = defaultMaxWorstLossYuan;
        this.defaultMaxBetPayoutYuan = defaultMaxBetPayoutYuan;
    }

    public EffectiveScopeRiskParams resolve(
            Map<String, ScopeRiskConfigLayer> layers,
            String fixtureId,
            String sportCode,
            String leagueCode) {
        String sport = blank(sportCode) ? "football" : sportCode.trim();
        String league = blank(leagueCode) ? "UNKNOWN" : leagueCode.trim();
        String match = blank(fixtureId) ? "" : fixtureId.trim();

        EffectiveScopeRiskParams base =
                EffectiveScopeRiskParams.fromCli(
                        defaultDelta,
                        defaultSeedPayoutYuan,
                        defaultMaxWorstLossYuan,
                        defaultMaxBetPayoutYuan);
        base = apply(base, get(layers, "OVERALL", "_"), "OVERALL");
        base = apply(base, get(layers, "SPORT", sport), "SPORT");
        base = apply(base, get(layers, "LEAGUE", sport + ":" + league), "LEAGUE");
        if (!match.isEmpty()) {
            base = apply(base, get(layers, "MATCH", match), "MATCH");
        }
        return base;
    }

    private static ScopeRiskConfigLayer get(
            Map<String, ScopeRiskConfigLayer> layers, String type, String key) {
        if (layers == null || layers.isEmpty()) {
            return null;
        }
        return layers.get(type + ":" + key);
    }

    private static EffectiveScopeRiskParams apply(
            EffectiveScopeRiskParams base, ScopeRiskConfigLayer ov, String source) {
        if (ov == null) {
            return base;
        }
        boolean limitsTouched =
                ov.delta != null
                        || ov.seedPayoutYuan != null
                        || ov.maxWorstLossYuan != null
                        || ov.maxBetPayoutYuan != null;
        return new EffectiveScopeRiskParams(
                ov.tradingEnabled != null ? ov.tradingEnabled : base.tradingEnabled,
                ov.limitGateEnabled != null ? ov.limitGateEnabled : base.limitGateEnabled,
                ov.exposureGateEnabled != null ? ov.exposureGateEnabled : base.exposureGateEnabled,
                ov.delta != null ? ov.delta : base.limitDelta,
                ov.seedPayoutYuan != null ? ov.seedPayoutYuan : base.seedPayoutYuan,
                ov.maxWorstLossYuan != null ? ov.maxWorstLossYuan : base.maxWorstLossYuan,
                ov.maxBetPayoutYuan != null ? ov.maxBetPayoutYuan : base.maxBetPayoutYuan,
                ov.tradingEnabled != null ? source : base.tradingSource,
                ov.limitGateEnabled != null ? source : base.limitGateSource,
                ov.exposureGateEnabled != null ? source : base.exposureGateSource,
                limitsTouched ? source : base.limitsSource);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
