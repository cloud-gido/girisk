package com.girisk.flink.risk.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.sink.legacy.RichSinkFunction;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * 将 Flink 限额快照的 {@code marketGroups} 写入与汇总同一 Redis hash，
 * 供 Console 责任盘「各盘口明细」与「拦截结果汇总」同源（girisk:view:fixture:{id}）。
 */
public final class RedisFixtureMarketGroupsSink extends RichSinkFunction<String> {

    private static final long serialVersionUID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TTL_SECONDS = 7 * 24 * 3600;

    private final String host;
    private final int port;
    private final String password;

    private transient JedisPool pool;

    public RedisFixtureMarketGroupsSink(String host, int port, String password) {
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
        // PASS：含 trigger 的组即本场已确认账本；拒单：用 prior（不含未接收单）
        String rejectReason = text(root, "rejectReason").toUpperCase();
        boolean accepted = rejectReason.isEmpty() || "NONE".equals(rejectReason);
        JsonNode marketGroups = accepted
                ? root.get("marketGroupsIncludingTrigger")
                : root.get("marketGroups");
        if (marketGroups == null || !marketGroups.isArray()) {
            marketGroups = root.get("marketGroups");
        }
        if (marketGroups == null || !marketGroups.isArray()) {
            return;
        }
        long updatedAt = root.path("publishedAtMs").asLong(System.currentTimeMillis());

        try (Jedis jedis = pool.getResource()) {
            String key = "girisk:view:fixture:" + fixtureId;
            jedis.hset(key, "marketGroups", MAPPER.writeValueAsString(marketGroups));
            jedis.hset(key, "marketGroupsUpdatedAt", String.valueOf(updatedAt));
            if (root.has("delta") && !root.path("delta").isNull()) {
                jedis.hset(key, "limitDelta", String.valueOf(root.path("delta").asDouble()));
            }
            if (root.has("initialSeedPayoutYuan") && !root.path("initialSeedPayoutYuan").isNull()) {
                jedis.hset(
                        key,
                        "initialSeedPayoutYuan",
                        String.valueOf(root.path("initialSeedPayoutYuan").asDouble()));
            }
            jedis.expire(key, TTL_SECONDS);
        }
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || n.isNull()) {
            return "";
        }
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }
}
