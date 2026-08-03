package com.girisk.stream;

import com.girisk.common.dto.RiskEvaluateResponse;
import com.girisk.config.RiskKafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "girisk.kafka.enabled", havingValue = "true")
public class OrderKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderKafkaPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OrderEventMapper mapper;
    private final RiskKafkaProperties properties;

    public OrderKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            OrderEventMapper mapper,
            RiskKafkaProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = mapper;
        this.properties = properties;
    }

    public void publishOrder(String orderJson) {
        try {
            kafkaTemplate.send(properties.getOrderTopic(), orderJson).get();
            log.debug("Published order event to {}", properties.getOrderTopic());
        } catch (Exception e) {
            log.error("Failed to publish order to Kafka topic={}", properties.getOrderTopic(), e);
            throw new IllegalStateException("Kafka 发送失败: " + e.getMessage(), e);
        }
    }

    public void publishDecision(RiskEvaluateResponse response) {
        try {
            String json = mapper.toJson(response);
            kafkaTemplate.send(properties.getDecisionTopic(), response.orderId(), json);
            log.debug("Published decision to {} orderId={}", properties.getDecisionTopic(), response.orderId());
        } catch (Exception e) {
            log.warn("Failed to publish decision to Kafka orderId={}", response.orderId(), e);
        }
    }

    public String orderTopic() { return properties.getOrderTopic(); }
    public String decisionTopic() { return properties.getDecisionTopic(); }
    public String bootstrapServers() { return properties.getBootstrapServers(); }
}
