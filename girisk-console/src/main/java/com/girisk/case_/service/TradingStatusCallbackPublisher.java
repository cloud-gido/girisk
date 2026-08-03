package com.girisk.case_.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.config.RiskKafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * REVIEW 审核结论回传交易：优先 Kafka status topic，其次可选 HTTP callback。
 */
@Component
public class TradingStatusCallbackPublisher {

    private static final Logger log = LoggerFactory.getLogger(TradingStatusCallbackPublisher.class);

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RiskKafkaProperties kafkaProperties;
    private final String callbackUrl;
    private final String statusTopic;
    private final RestClient restClient = RestClient.create();

    public TradingStatusCallbackPublisher(
            ObjectMapper objectMapper,
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplate,
            ObjectProvider<RiskKafkaProperties> kafkaProperties,
            @Value("${girisk.review.callback-url:}") String callbackUrl,
            @Value("${girisk.kafka.status-topic:girisk.trading.order.risk-check.post.v1}") String statusTopic) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate.getIfAvailable();
        this.kafkaProperties = kafkaProperties.getIfAvailable();
        this.callbackUrl = callbackUrl;
        this.statusTopic = statusTopic;
    }

    public CallbackResult publish(Map<String, Object> payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            json = payload.toString();
        }

        boolean sent = false;
        String channel = "LOCAL_ONLY";
        String detail = "no outbound channel configured";

        if (kafkaTemplate != null && kafkaProperties != null && kafkaProperties.isEnabled()) {
            try {
                String topic = kafkaProperties.getStatusTopic() != null && !kafkaProperties.getStatusTopic().isBlank()
                        ? kafkaProperties.getStatusTopic()
                        : statusTopic;
                String orderId = String.valueOf(payload.getOrDefault("orderId", ""));
                kafkaTemplate.send(topic, orderId, json).get();
                sent = true;
                channel = "KAFKA:" + topic;
                detail = "published";
                log.info("REVIEW callback published to Kafka topic={} orderId={}", topic, orderId);
            } catch (Exception e) {
                detail = "kafka failed: " + e.getMessage();
                log.warn("REVIEW Kafka callback failed: {}", e.getMessage());
            }
        }

        if (!callbackUrl.isBlank()) {
            try {
                restClient.post()
                        .uri(callbackUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json)
                        .retrieve()
                        .toBodilessEntity();
                sent = true;
                channel = sent && channel.startsWith("KAFKA") ? channel + "+HTTP" : "HTTP:" + callbackUrl;
                detail = "delivered";
                log.info("REVIEW callback POSTed to {}", callbackUrl);
            } catch (Exception e) {
                detail = detail + "; http failed: " + e.getMessage();
                log.warn("REVIEW HTTP callback failed: {}", e.getMessage());
            }
        }

        return new CallbackResult(sent, channel, detail, json);
    }

    public record CallbackResult(boolean sent, String channel, String detail, String payloadJson) {}
}
