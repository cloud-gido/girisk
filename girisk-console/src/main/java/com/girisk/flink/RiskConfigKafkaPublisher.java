package com.girisk.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.common.RiskTopics;
import com.girisk.common.exception.BusinessException;
import com.girisk.config.RiskKafkaProperties;
import com.girisk.configcenter.model.RiskConfigRelease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Publishes approved config releases to girisk.config.v1 for Flink（同步 ack + 重试）. */
@Component
@ConditionalOnProperty(name = "girisk.kafka.enabled", havingValue = "true")
public class RiskConfigKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(RiskConfigKafkaPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RiskKafkaProperties properties;
    private final ObjectMapper objectMapper;

    public RiskConfigKafkaPublisher(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplate,
            RiskKafkaProperties properties,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate.getIfAvailable();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void publish(RiskConfigRelease release) {
        if (kafkaTemplate == null || release == null) {
            throw new BusinessException("Kafka 未启用，无法发布配置");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schemaVersion", "1");
            payload.put("kind", "GLOBAL_RELEASE");
            payload.put("configEpoch", release.configEpoch());
            payload.put("scope", "global");
            payload.put("approvalTicket", release.approvalTicket());
            payload.put("paramSetJson", release.paramSetJson());
            payload.put("ruleSetJson", release.ruleSetJson());
            payload.put("publishedBy", release.publishedBy());
            payload.put("publishedAt", release.publishedAt() == null ? null : release.publishedAt().toString());
            String topic = properties.getConfigTopic() == null || properties.getConfigTopic().isBlank()
                    ? RiskTopics.RISK_CONFIG
                    : properties.getConfigTopic();
            String json = objectMapper.writeValueAsString(payload);
            int maxAttempts = Math.max(1, properties.getConfigPublishMaxAttempts());
            long backoff = Math.max(50L, properties.getConfigPublishBackoffMs());
            int timeoutSec = Math.max(1, properties.getConfigPublishTimeoutSeconds());
            Exception last = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    SendResult<String, String> result =
                            kafkaTemplate.send(topic, "OVERALL:_", json).get(timeoutSec, TimeUnit.SECONDS);
                    log.info(
                            "Published GLOBAL_RELEASE epoch={} → {} offset={} attempt={}",
                            release.configEpoch(),
                            topic,
                            result.getRecordMetadata().offset(),
                            attempt);
                    return;
                } catch (Exception e) {
                    last = e;
                    log.warn("GLOBAL_RELEASE publish attempt {}/{} failed: {}", attempt, maxAttempts, e.getMessage());
                    if (attempt < maxAttempts) {
                        try {
                            Thread.sleep(backoff * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new BusinessException("配置发布被中断");
                        }
                    }
                }
            }
            throw new BusinessException(
                    "配置发布到 girisk.config.v1 失败: "
                            + (last == null ? "unknown" : last.getMessage()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("配置发布失败: " + e.getMessage());
        }
    }
}
