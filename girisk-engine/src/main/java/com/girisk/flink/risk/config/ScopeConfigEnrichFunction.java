package com.girisk.flink.risk.config;

import com.girisk.flink.risk.model.RiskOrderStreamEvent;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.util.Collector;

import java.util.HashMap;
import java.util.Map;

/**
 * Attaches {@link EffectiveScopeRiskParams} onto each {@link RiskOrderStreamEvent} from
 * BroadcastState of SCOPE_OVERRIDE layers.
 */
public final class ScopeConfigEnrichFunction
        extends KeyedBroadcastProcessFunction<
                String, RiskOrderStreamEvent, String, RiskOrderStreamEvent> {

    private static final long serialVersionUID = 1L;

    public static final MapStateDescriptor<String, ScopeRiskConfigLayer> CONFIG_STATE_DESC =
            new MapStateDescriptor<>(
                    "scope-risk-config-layers",
                    TypeInformation.of(String.class),
                    TypeInformation.of(new TypeHint<ScopeRiskConfigLayer>() {}));

    private final ScopeRiskConfigResolver resolver;
    private final ScopeRiskConfigParser parser = new ScopeRiskConfigParser();

    public ScopeConfigEnrichFunction(
            double defaultDelta, double defaultSeedPayoutYuan, double defaultMaxWorstLossYuan) {
        this(defaultDelta, defaultSeedPayoutYuan, defaultMaxWorstLossYuan, 0.0);
    }

    public ScopeConfigEnrichFunction(
            double defaultDelta,
            double defaultSeedPayoutYuan,
            double defaultMaxWorstLossYuan,
            double defaultMaxBetPayoutYuan) {
        this.resolver =
                new ScopeRiskConfigResolver(
                        defaultDelta,
                        defaultSeedPayoutYuan,
                        defaultMaxWorstLossYuan,
                        defaultMaxBetPayoutYuan);
    }

    @Override
    public void processBroadcastElement(
            String value, Context ctx, Collector<RiskOrderStreamEvent> out) throws Exception {
        ScopeRiskConfigParser.Parsed parsed = parser.parse(value);
        if (parsed.action == ScopeRiskConfigParser.Action.IGNORE || parsed.layer == null) {
            return;
        }
        var state = ctx.getBroadcastState(CONFIG_STATE_DESC);
        String key = parsed.layer.mapKey();
        if (parsed.action == ScopeRiskConfigParser.Action.DELETE) {
            state.remove(key);
            System.out.printf("[config.v1] removed %s%n", key);
            return;
        }
        state.put(key, parsed.layer);
        System.out.printf(
                "[config.v1] upsert %s epoch=%d trading=%s limitGate=%s exposureGate=%s delta=%s%n",
                key,
                parsed.layer.configEpoch,
                parsed.layer.tradingEnabled,
                parsed.layer.limitGateEnabled,
                parsed.layer.exposureGateEnabled,
                parsed.layer.delta);
    }

    @Override
    public void processElement(
            RiskOrderStreamEvent value, ReadOnlyContext ctx, Collector<RiskOrderStreamEvent> out)
            throws Exception {
        Map<String, ScopeRiskConfigLayer> layers = new HashMap<>();
        for (Map.Entry<String, ScopeRiskConfigLayer> e :
                ctx.getBroadcastState(CONFIG_STATE_DESC).immutableEntries()) {
            layers.put(e.getKey(), e.getValue());
        }
        String fixtureId = value.fixtureIdForKey();
        String league = "";
        String sport = "football";
        if (value.kind == RiskOrderStreamEvent.Kind.PRE_PENDING
                && value.prePending != null
                && value.prePending.order != null) {
            league = value.prePending.order.league;
            if (value.prePending.order.sportCode != null
                    && !value.prePending.order.sportCode.isBlank()) {
                sport = value.prePending.order.sportCode;
            }
        } else if (value.kind == RiskOrderStreamEvent.Kind.POST_STATUS
                && value.postUpdate != null
                && value.postUpdate.order != null) {
            league = value.postUpdate.order.league;
            if (value.postUpdate.order.sportCode != null
                    && !value.postUpdate.order.sportCode.isBlank()) {
                sport = value.postUpdate.order.sportCode;
            }
        }
        value.scopeParams = resolver.resolve(layers, fixtureId, sport, league);
        out.collect(value);
    }
}
