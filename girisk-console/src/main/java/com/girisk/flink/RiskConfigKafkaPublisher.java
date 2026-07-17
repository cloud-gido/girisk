package com.girisk.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.common.RiskTopics;
import com.girisk.config.RiskKafkaProperties;
import com.girisk.configcenter.model.RiskConfigRelease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Publishes approved config releases to girisk.config.v1 for Flink. */
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
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schemaVersion", "1");
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
            kafkaTemplate.send(topic, "global", objectMapper.writeValueAsString(payload));
            log.info("Published config epoch={} to {}", release.configEpoch(), topic);
        } catch (Exception e) {
            log.warn("Failed to publish config to Kafka: {}", e.getMessage());
        }
    }
}
