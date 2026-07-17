package com.girisk.flink.risk.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.sink.legacy.RichSinkFunction;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Writes Flink summary snapshots into Redis materialised views for the console dashboard.
 * Keys: girisk:view:fixture:{id}, girisk:view:top:worstloss.
 */
public final class RedisFixtureViewSink extends RichSinkFunction<String> {

    private static final long serialVersionUID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String host;
    private final int port;
    private final String password;

    private transient JedisPool pool;

    public RedisFixtureViewSink(String host, int port, String password) {
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
            fixtureId = text(root.path("match"), "fixtureId");
        }
        if (fixtureId.isEmpty()) {
            return;
        }
        long worstLossCents = yuanToCents(root.path("worstLossYuan").asDouble(0));
        if (worstLossCents == 0 && root.has("exposure")) {
            worstLossCents = yuanToCents(root.path("exposure").path("worstLossYuan").asDouble(0));
        }
        String worstScore = text(root, "worstScore");
        if (worstScore.isEmpty() && root.has("exposure")) {
            worstScore = text(root.path("exposure"), "worstScore");
        }
        int confirmed = root.path("windowOrderCount").asInt(root.path("confirmedOrderCount").asInt(0));
        String liveScore = text(root, "liveScore");
        long updatedAt = root.path("publishedAtMs").asLong(System.currentTimeMillis());

        try (Jedis jedis = pool.getResource()) {
            String key = "girisk:view:fixture:" + fixtureId;
            jedis.hset(key, "fixtureId", fixtureId);
            jedis.hset(key, "worstLossCents", String.valueOf(Math.abs(worstLossCents)));
            jedis.hset(key, "worstScore", worstScore);
            jedis.hset(key, "confirmedOrders", String.valueOf(confirmed));
            jedis.hset(key, "liveScore", liveScore);
            jedis.hset(key, "updatedAt", String.valueOf(updatedAt));
            jedis.hset(key, "rawSnapshot", value.length() > 4000 ? value.substring(0, 4000) : value);
            jedis.expire(key, 7 * 24 * 3600);
            if (worstLossCents != 0) {
                jedis.zadd("girisk:view:top:worstloss", Math.abs(worstLossCents), fixtureId);
            }
        }
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }

    private static long yuanToCents(double yuan) {
        return Math.round(yuan * 100.0);
    }
}
