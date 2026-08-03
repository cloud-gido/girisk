package com.girisk.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "girisk.kafka.enabled", havingValue = "true")
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderStreamProcessor processor;

    public OrderEventConsumer(OrderStreamProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = "${girisk.kafka.order-topic}", groupId = "${girisk.kafka.consumer-group}")
    public void onOrderEvent(String message) {
        try {
            processor.processJson(message, "KAFKA");
            log.debug("Consumed order event from Kafka");
        } catch (Exception e) {
            log.error("Kafka consumer failed to process message", e);
        }
    }
}
