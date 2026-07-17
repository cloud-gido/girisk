package com.girisk.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "girisk.kafka.enabled", havingValue = "true")
public class KafkaTopicBootstrap implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicBootstrap.class);

    private final RiskKafkaProperties properties;

    public KafkaTopicBootstrap(RiskKafkaProperties properties) {
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Thread.startVirtualThread(this::ensureTopicsWithRetry);
    }

    private void ensureTopicsWithRetry() {
        int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ensureTopics();
                return;
            } catch (Exception e) {
                log.warn("Kafka topic bootstrap attempt {}/{} failed: {}", attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    sleep(3000L * attempt);
                }
            }
        }
        log.error("Kafka topic bootstrap failed after {} attempts, topics may need manual creation", maxAttempts);
    }

    private void ensureTopics() throws Exception {
        Map<String, Object> configs = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers(),
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 15000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 15000);

        try (AdminClient admin = AdminClient.create(configs)) {
            Set<String> existing = admin.listTopics().names().get(20, TimeUnit.SECONDS);
            createIfMissing(admin, existing, properties.getOrderTopic());
            createIfMissing(admin, existing, properties.getDecisionTopic());
        }
    }

    private void createIfMissing(AdminClient admin, Set<String> existing, String topicName) throws Exception {
        if (existing.contains(topicName)) {
            log.info("Kafka topic already exists: {}", topicName);
            return;
        }
        NewTopic topic = new NewTopic(topicName, properties.getTopicPartitions(), properties.getTopicReplicas());
        try {
            admin.createTopics(List.of(topic)).all().get(20, TimeUnit.SECONDS);
            log.info("Kafka topic created: {} (partitions={}, replicas={})",
                    topicName, properties.getTopicPartitions(), properties.getTopicReplicas());
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                log.info("Kafka topic already exists (race): {}", topicName);
                return;
            }
            throw e;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
