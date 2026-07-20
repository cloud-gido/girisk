package com.girisk.flink.risk.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.common.config.ScopeRiskConfigKinds;
import com.girisk.common.config.ScopeRiskConfigMessage;

import java.io.Serializable;

public final class ScopeRiskConfigParser implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum Action {
        UPSERT,
        DELETE,
        IGNORE
    }

    public static final class Parsed implements Serializable {
        private static final long serialVersionUID = 1L;
        public final Action action;
        public final ScopeRiskConfigLayer layer;

        public Parsed(Action action, ScopeRiskConfigLayer layer) {
            this.action = action;
            this.layer = layer;
        }
    }

    public Parsed parse(String json) {
        if (json == null || json.isBlank()) {
            return new Parsed(Action.IGNORE, null);
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            String kind = text(root, "kind");
            // Legacy Config Center release → map to OVERALL limits only
            if (kind == null || kind.isBlank() || ScopeRiskConfigKinds.GLOBAL_RELEASE.equals(kind)
                    || root.has("paramSetJson")) {
                return parseGlobalRelease(root);
            }
            if (!ScopeRiskConfigKinds.SCOPE_OVERRIDE.equals(kind)) {
                return new Parsed(Action.IGNORE, null);
            }
            ScopeRiskConfigMessage msg = MAPPER.treeToValue(root, ScopeRiskConfigMessage.class);
            if (msg.deleted) {
                ScopeRiskConfigLayer tomb = new ScopeRiskConfigLayer();
                tomb.scopeType = msg.scopeType;
                tomb.scopeKey = msg.scopeKey;
                return new Parsed(Action.DELETE, tomb);
            }
            ScopeRiskConfigLayer layer = new ScopeRiskConfigLayer();
            layer.scopeType = msg.scopeType == null ? "OVERALL" : msg.scopeType.trim().toUpperCase();
            layer.scopeKey = msg.scopeKey == null || msg.scopeKey.isBlank() ? "_" : msg.scopeKey.trim();
            layer.configEpoch = msg.configEpoch;
            if (msg.gates != null) {
                layer.tradingEnabled = msg.gates.tradingEnabled;
                layer.limitGateEnabled = msg.gates.limitGateEnabled;
                layer.exposureGateEnabled = msg.gates.exposureGateEnabled;
            }
            if (msg.limits != null) {
                layer.delta = msg.limits.delta;
                layer.seedPayoutYuan = msg.limits.seedPayoutYuan;
                layer.maxWorstLossYuan = msg.limits.maxWorstLossYuan;
                layer.maxBetPayoutYuan = msg.limits.maxBetPayoutYuan;
            }
            return new Parsed(Action.UPSERT, layer);
        } catch (Exception e) {
            System.err.println("[config.v1] parse failed: " + e.getMessage());
            return new Parsed(Action.IGNORE, null);
        }
    }

    private Parsed parseGlobalRelease(JsonNode root) {
        try {
            ScopeRiskConfigLayer layer = new ScopeRiskConfigLayer();
            layer.scopeType = "OVERALL";
            layer.scopeKey = "_";
            layer.configEpoch = root.path("configEpoch").asLong(0L);
            String paramSetJson = text(root, "paramSetJson");
            if (paramSetJson == null || paramSetJson.isBlank()) {
                return new Parsed(Action.IGNORE, null);
            }
            JsonNode ps = MAPPER.readTree(paramSetJson);
            JsonNode limit = ps.path("limit");
            JsonNode exposure = ps.path("exposure");
            if (limit.has("delta")) {
                layer.delta = limit.path("delta").asDouble();
            }
            if (limit.has("initialSeedPayoutCents")) {
                layer.seedPayoutYuan = limit.path("initialSeedPayoutCents").asDouble() / 100.0;
            }
            if (limit.has("maxBetPayoutCents")) {
                layer.maxBetPayoutYuan = limit.path("maxBetPayoutCents").asDouble() / 100.0;
            } else if (limit.has("maxBetPayoutYuan")) {
                layer.maxBetPayoutYuan = limit.path("maxBetPayoutYuan").asDouble();
            }
            if (exposure.has("maxWorstLossCents")) {
                layer.maxWorstLossYuan = exposure.path("maxWorstLossCents").asDouble() / 100.0;
            }
            return new Parsed(Action.UPSERT, layer);
        } catch (Exception e) {
            return new Parsed(Action.IGNORE, null);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
