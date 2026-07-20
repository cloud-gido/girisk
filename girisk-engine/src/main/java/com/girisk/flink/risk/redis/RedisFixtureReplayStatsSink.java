package com.girisk.flink.risk.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.sink.legacy.RichSinkFunction;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * 按 decision.v1 累加本场 {@code replayStats}，与离线回放写入的字段对齐，
 * 便于 Console「拦截结果汇总」实时/回放同一套 12 格指标。
 */
public final class RedisFixtureReplayStatsSink extends RichSinkFunction<String> {

    private static final long serialVersionUID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String host;
    private final int port;
    private final String password;

    private transient JedisPool pool;

    public RedisFixtureReplayStatsSink(String host, int port, String password) {
        this.host = host == null || host.isBlank() ? "127.0.0.1" : host;
        this.port = port <= 0 ? 6379 : port;
        this.password = password;
    }

    @Override
    public void open(OpenContext openContext) {
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(8);
        if (password != null && !password.isBlank()) {
            pool = new JedisPool(cfg, host, port, 2000, password);
        } else {
            pool = new JedisPool(cfg, host, port, 2000);
        }
    }

    @Override
    public void invoke(String value, Context context) throws Exception {
        if (value == null || value.isBlank() || pool == null) {
            return;
        }
        JsonNode root = MAPPER.readTree(value);
        String fixtureId = text(root, "fixtureId");
        if (fixtureId.isEmpty()) {
            return;
        }
        String decision = text(root, "decision").toUpperCase();
        if (decision.isEmpty()) {
            return;
        }

        JsonNode evidence = root.path("evidence");
        JsonNode after = root.path("featureSnapshot").path("afterActual");
        boolean pass = "PASS".equals(decision);
        boolean limitRejected = evidence.path("limitRejected").asBoolean(false);
        boolean exposureRejected = evidence.path("exposureRejected").asBoolean(false);

        try (Jedis jedis = pool.getResource()) {
            String key = "girisk:view:fixture:" + fixtureId;
            ObjectNode stats = readStats(jedis.hget(key, "replayStats"));

            // 接单数由 summary→RedisFixtureViewSink 覆盖；此处只累加拒单/重复。
            // 有效订单 = acceptedCount + rejectedTotal + duplicateCount（由 ViewSink 回写）。
            boolean duplicate = evidence.path("duplicateIgnored").asBoolean(false);
            applyDecisionCounts(stats, pass, duplicate, limitRejected, exposureRejected);

            if (evidence.has("limitDelta") && !evidence.path("limitDelta").isNull()) {
                stats.put("delta", evidence.path("limitDelta").asDouble());
            }
            if (evidence.has("seedPayoutYuan") && !evidence.path("seedPayoutYuan").isNull()) {
                stats.put("seedPayoutYuan", evidence.path("seedPayoutYuan").asDouble());
            }
            if (evidence.has("maxWorstLossYuan") && !evidence.path("maxWorstLossYuan").isNull()) {
                stats.put("maxWorstLossYuan", evidence.path("maxWorstLossYuan").asDouble());
            }

            // 拦截后最差：用实际接收后快照（拒单时 afterActual 仍为接收前窗口）
            if (after.has("worstBookmakerPnlYuan") && !after.path("worstBookmakerPnlYuan").isNull()) {
                stats.put("withRiskWorstPnlYuan", after.path("worstBookmakerPnlYuan").asDouble());
            }
            String worstScore = text(after, "worstScore");
            if (!worstScore.isEmpty()) {
                stats.put("withRiskWorstScore", worstScore);
            }

            JsonNode feature = root.path("featureSnapshot");
            if (feature.has("noRiskWorstPnlYuan") && !feature.path("noRiskWorstPnlYuan").isNull()) {
                stats.put("noRiskWorstPnlYuan", feature.path("noRiskWorstPnlYuan").asDouble());
            }
            String noRiskScore = text(feature, "noRiskWorstScore");
            if (!noRiskScore.isEmpty()) {
                stats.put("noRiskWorstScore", noRiskScore);
            }

            jedis.hset(key, "replayStats", MAPPER.writeValueAsString(stats));
            jedis.hset(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
            jedis.expire(key, 7 * 24 * 3600);
        }
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }

    private static ObjectNode readStats(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return MAPPER.createObjectNode();
        }
        JsonNode n = MAPPER.readTree(raw);
        if (n instanceof ObjectNode) {
            return (ObjectNode) n;
        }
        return MAPPER.createObjectNode();
    }

    /**
     * 累加拒单/重复。不再累加 totalOrders（避免与接单窗口漂移）；
     * 有效数由 ViewSink 写成 accepted+rejected+duplicate。
     * 重复单 decision 仍为 PASS，但 evidence.duplicateIgnored=true。
     */
    static void applyDecisionCounts(
            ObjectNode stats,
            boolean pass,
            boolean duplicate,
            boolean limitRejected,
            boolean exposureRejected) {
        if (duplicate) {
            bump(stats, "duplicateCount", 1);
            return;
        }
        if (!pass) {
            bump(stats, "rejectedTotal", 1);
            if (limitRejected) {
                bump(stats, "rejectedLimit", 1);
            }
            if (exposureRejected) {
                bump(stats, "rejectedExposure", 1);
            }
        }
    }

    private static void bump(ObjectNode stats, String field, double delta) {
        double cur = stats.path(field).asDouble(0);
        if ("acceptedStakeYuan".equals(field) || field.endsWith("Yuan")) {
            stats.put(field, Math.round((cur + delta) * 100.0) / 100.0);
        } else {
            stats.put(field, (long) Math.round(cur + delta));
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }
}
