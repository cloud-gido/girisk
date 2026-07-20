package com.girisk.sports.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.girisk.config.RiskKafkaProperties;
import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeGateOverride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "girisk.redis.enabled", havingValue = "true")
public class RedisScopeRiskConfigOutbox implements ScopeRiskConfigOutbox {

    private static final Logger log = LoggerFactory.getLogger(RedisScopeRiskConfigOutbox.class);

    static final String QUEUE_KEY = "girisk:outbox:scope-config";
    static final String DLQ_KEY = "girisk:outbox:scope-config:dlq";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RiskKafkaProperties kafkaProperties;

    public RedisScopeRiskConfigOutbox(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            RiskKafkaProperties kafkaProperties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.kafkaProperties = kafkaProperties;
    }

    @Override
    public boolean isRelayEnabled() {
        return kafkaProperties.isEnabled() && kafkaProperties.isConfigOutboxEnabled();
    }

    @Override
    public void enqueue(LimitScopeType type, String scopeKey) {
        if (!isRelayEnabled() || type == null || scopeKey == null || scopeKey.isBlank()) {
            return;
        }
        redis.opsForList().rightPush(QUEUE_KEY, toJson(entry(type, scopeKey, 0)));
    }

    @Override
    public void enqueueInTransaction(RedisOperations<String, String> ops, LimitScopeType type, String scopeKey) {
        if (!isRelayEnabled() || type == null || scopeKey == null || scopeKey.isBlank() || ops == null) {
            return;
        }
        ops.opsForList().rightPush(QUEUE_KEY, toJson(entry(type, scopeKey, 0)));
    }

    @Override
    public Optional<ScopeRiskConfigOutboxEntry> claim() {
        String json = redis.opsForList().leftPop(QUEUE_KEY);
        return parse(json);
    }

    @Override
    public void requeue(ScopeRiskConfigOutboxEntry entry) {
        if (entry == null) {
            return;
        }
        redis.opsForList().rightPush(QUEUE_KEY, toJson(entry.withAttempt(entry.attempts() + 1)));
    }

    @Override
    public void deadLetter(ScopeRiskConfigOutboxEntry entry, String reason) {
        if (entry == null) {
            return;
        }
        try {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("scopeType", entry.scopeType().name());
            n.put("scopeKey", entry.scopeKey());
            n.put("enqueuedAt", entry.enqueuedAtEpochMs());
            n.put("attempts", entry.attempts());
            n.put("reason", reason == null ? "" : reason);
            n.put("deadAt", System.currentTimeMillis());
            redis.opsForList().rightPush(DLQ_KEY, objectMapper.writeValueAsString(n));
            log.error(
                    "config outbox DLQ scope={}/{} attempts={} reason={}",
                    entry.scopeType(),
                    entry.scopeKey(),
                    entry.attempts(),
                    reason);
        } catch (Exception e) {
            log.error("config outbox DLQ write failed: {}", e.getMessage());
        }
    }

    @Override
    public long pendingDepth() {
        Long n = redis.opsForList().size(QUEUE_KEY);
        return n == null ? 0L : n;
    }

    private ScopeRiskConfigOutboxEntry entry(LimitScopeType type, String scopeKey, int attempts) {
        String key = ScopeGateOverride.normalizeKey(type, scopeKey);
        return new ScopeRiskConfigOutboxEntry(type, key, System.currentTimeMillis(), attempts);
    }

    private String toJson(ScopeRiskConfigOutboxEntry e) {
        try {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("scopeType", e.scopeType().name());
            n.put("scopeKey", e.scopeKey());
            n.put("enqueuedAt", e.enqueuedAtEpochMs());
            n.put("attempts", e.attempts());
            return objectMapper.writeValueAsString(n);
        } catch (Exception ex) {
            throw new IllegalStateException("outbox serialize failed: " + ex.getMessage(), ex);
        }
    }

    private Optional<ScopeRiskConfigOutboxEntry> parse(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            LimitScopeType type = LimitScopeType.valueOf(n.path("scopeType").asText().toUpperCase(Locale.ROOT));
            String key = n.path("scopeKey").asText();
            if (key == null || key.isBlank()) {
                return Optional.empty();
            }
            long at = n.path("enqueuedAt").asLong(System.currentTimeMillis());
            int attempts = n.path("attempts").asInt(0);
            return Optional.of(new ScopeRiskConfigOutboxEntry(type, key, at, attempts));
        } catch (Exception e) {
            log.warn("config outbox skip bad payload: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
