package com.girisk.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Redis {@code girisk:config:audit:doris}；无 Redis 时进程内内存。 */
@Component
public class DorisAuditSettingsStore {

    private static final Logger log = LoggerFactory.getLogger(DorisAuditSettingsStore.class);
    public static final String REDIS_KEY = "girisk:config:audit:doris";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AtomicReference<DorisAuditRuntimeSettings> memory = new AtomicReference<>();

    public DorisAuditSettingsStore(
            ObjectProvider<StringRedisTemplate> redis, ObjectMapper objectMapper) {
        this.redis = redis.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    public Optional<DorisAuditRuntimeSettings> load() {
        if (redis != null) {
            try {
                String raw = redis.opsForValue().get(REDIS_KEY);
                if (raw != null && !raw.isBlank()) {
                    return Optional.of(objectMapper.readValue(raw, DorisAuditRuntimeSettings.class));
                }
            } catch (Exception e) {
                log.warn("load Doris audit settings from Redis failed: {}", e.getMessage());
            }
        }
        return Optional.ofNullable(memory.get()).map(DorisAuditRuntimeSettings::copy);
    }

    public void save(DorisAuditRuntimeSettings settings) {
        DorisAuditRuntimeSettings copy = settings.copy();
        memory.set(copy);
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(REDIS_KEY, objectMapper.writeValueAsString(copy));
        } catch (Exception e) {
            log.warn("save Doris audit settings to Redis failed: {}", e.getMessage());
            throw new IllegalStateException("保存 Doris 配置失败: " + e.getMessage(), e);
        }
    }
}
