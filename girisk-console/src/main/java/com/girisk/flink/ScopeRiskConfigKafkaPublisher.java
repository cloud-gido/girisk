package com.girisk.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.common.RiskTopics;
import com.girisk.common.config.ScopeRiskConfigKinds;
import com.girisk.common.config.ScopeRiskConfigMessage;
import com.girisk.common.exception.BusinessException;
import com.girisk.config.RiskKafkaProperties;
import com.girisk.event.repository.RiskEventRepository;
import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeGateOverride;
import com.girisk.sports.model.ScopeLimitOverride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 值班门控/限额 → {@code girisk.config.v1}。
 * 生产级：acks=all（Producer 层）+ 同步 get + 应用重试；失败写事件并抛错。
 * 写路径经 Redis outbox 时由 Poller 调用；失败会 requeue / DLQ，HTTP 不阻塞。
 */
@Component
@ConditionalOnProperty(name = "girisk.kafka.enabled", havingValue = "true")
public class ScopeRiskConfigKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(ScopeRiskConfigKafkaPublisher.class);
    private static final AtomicLong EPOCH = new AtomicLong(System.currentTimeMillis());

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RiskKafkaProperties properties;
    private final ObjectMapper objectMapper;
    private final RiskEventRepository eventRepository;

    public ScopeRiskConfigKafkaPublisher(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplate,
            RiskKafkaProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<RiskEventRepository> eventRepository) {
        this.kafkaTemplate = kafkaTemplate.getIfAvailable();
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventRepository = eventRepository.getIfAvailable();
    }

    /** 发布本层完整快照；失败抛 {@link BusinessException}（本地 Redis 可能已写，需重试或启动同步修复）。 */
    public void publishSnapshot(
            LimitScopeType type,
            String scopeKey,
            ScopeGateOverride gates,
            ScopeLimitOverride limits,
            boolean deleted) {
        ScopeRiskConfigMessage msg = base(type, scopeKey, deleted);
        if (!deleted) {
            if (gates != null && gates.hasAny()) {
                ScopeRiskConfigMessage.Gates g = new ScopeRiskConfigMessage.Gates();
                g.tradingEnabled = gates.tradingEnabled();
                g.limitGateEnabled = gates.limitGateEnabled();
                g.exposureGateEnabled = gates.exposureGateEnabled();
                msg.gates = g;
                msg.publishedBy = gates.updatedBy();
            }
            if (limits != null && limits.hasAny()) {
                ScopeRiskConfigMessage.Limits lim = new ScopeRiskConfigMessage.Limits();
                lim.delta = bd(limits.delta());
                lim.seedPayoutYuan = bd(limits.seedPayoutYuan());
                lim.maxWorstLossYuan = bd(limits.maxWorstLossYuan());
                lim.maxBetPayoutYuan = bd(limits.maxBetPayoutYuan());
                msg.limits = lim;
                if (msg.publishedBy == null) {
                    msg.publishedBy = limits.updatedBy();
                }
            }
        }
        sendReliable(msg);
    }

    private ScopeRiskConfigMessage base(LimitScopeType type, String scopeKey, boolean deleted) {
        ScopeRiskConfigMessage msg = new ScopeRiskConfigMessage();
        msg.schemaVersion = 1;
        msg.kind = ScopeRiskConfigKinds.SCOPE_OVERRIDE;
        msg.configEpoch = EPOCH.incrementAndGet();
        msg.scopeType = type.name();
        msg.scopeKey = ScopeGateOverride.normalizeKey(type, scopeKey);
        msg.deleted = deleted;
        msg.publishedAt = Instant.now().toString();
        return msg;
    }

    private void sendReliable(ScopeRiskConfigMessage msg) {
        if (kafkaTemplate == null) {
            throw new BusinessException("Kafka 未启用，无法同步配置到 Flink");
        }
        String topic = properties.getConfigTopic() == null || properties.getConfigTopic().isBlank()
                ? RiskTopics.RISK_CONFIG
                : properties.getConfigTopic();
        String key = ScopeRiskConfigMessage.kafkaKey(msg.scopeType, msg.scopeKey);
        String json;
        try {
            json = objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            throw new BusinessException("配置序列化失败: " + e.getMessage());
        }

        int maxAttempts = Math.max(1, properties.getConfigPublishMaxAttempts());
        long backoff = Math.max(50L, properties.getConfigPublishBackoffMs());
        int timeoutSec = Math.max(1, properties.getConfigPublishTimeoutSeconds());
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                SendResult<String, String> result =
                        kafkaTemplate.send(topic, key, json).get(timeoutSec, TimeUnit.SECONDS);
                var meta = result.getRecordMetadata();
                log.info(
                        "Published SCOPE_OVERRIDE key={} deleted={} epoch={} → {} partition={} offset={} attempt={}",
                        key,
                        msg.deleted,
                        msg.configEpoch,
                        topic,
                        meta.partition(),
                        meta.offset(),
                        attempt);
                return;
            } catch (Exception e) {
                last = e;
                log.warn(
                        "Publish SCOPE_OVERRIDE failed attempt={}/{} key={}: {}",
                        attempt,
                        maxAttempts,
                        key,
                        e.getMessage());
                if (attempt < maxAttempts) {
                    sleep(backoff * attempt);
                }
            }
        }
        String detail = "key=" + key + " topic=" + topic + " err="
                + (last == null ? "unknown" : last.getMessage());
        if (eventRepository != null) {
            try {
                eventRepository.insert(
                        "CONFIG_PUBLISH_FAIL",
                        "ERROR",
                        null,
                        msg.publishedBy,
                        "config.v1 同步失败",
                        detail);
            } catch (Exception ignored) {
                // best-effort audit
            }
        }
        throw new BusinessException(
                "配置已写入本地，但同步 Flink（girisk.config.v1）失败，请重试。「" + detail + "」");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Double bd(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}
