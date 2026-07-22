package com.girisk.config;

import com.girisk.sports.service.ScopeRiskConfigBootstrapSync;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
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
    private final ObjectProvider<ScopeRiskConfigBootstrapSync> configBootstrapSync;
    private final Environment environment;

    public KafkaTopicBootstrap(
            RiskKafkaProperties properties,
            ObjectProvider<ScopeRiskConfigBootstrapSync> configBootstrapSync,
            Environment environment) {
        this.properties = properties;
        this.configBootstrapSync = configBootstrapSync;
        this.environment = environment;
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
                if (properties.isConfigBootstrapSyncEnabled()) {
                    ScopeRiskConfigBootstrapSync sync = configBootstrapSync.getIfAvailable();
                    if (sync != null) {
                        sync.syncAllQuietly();
                    }
                }
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
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 15000);
        configs.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 15000);
        // MSK：必须与 consumer 相同的 SASL_SSL（由 KafkaSaslEnvironmentPostProcessor 注入）
        putIfPresent(configs, "security.protocol",
                environment.getProperty("girisk.kafka.security.protocol"),
                environment.getProperty("spring.kafka.properties.security.protocol"));
        putIfPresent(configs, "sasl.mechanism",
                environment.getProperty("girisk.kafka.sasl.mechanism"),
                environment.getProperty("spring.kafka.properties.sasl.mechanism"));
        putIfPresent(configs, "sasl.jaas.config",
                environment.getProperty("girisk.kafka.sasl.jaas.config"),
                environment.getProperty("spring.kafka.properties.sasl.jaas.config"));

        try (AdminClient admin = AdminClient.create(configs)) {
            Set<String> existing = admin.listTopics().names().get(20, TimeUnit.SECONDS);
            createIfMissing(admin, existing, properties.getOrderTopic(), false);
            createIfMissing(admin, existing, properties.getDecisionTopic(), false);
            createIfMissing(admin, existing, properties.getFlinkDecisionTopic(), false);
            createIfMissing(admin, existing, properties.getStatusTopic(), false);
            createIfMissing(admin, existing, properties.getConfigTopic(), true);
            ensureCompactPolicy(admin, properties.getConfigTopic());
        }
    }

    private static void putIfPresent(Map<String, Object> configs, String key, String... candidates) {
        for (String v : candidates) {
            if (v != null && !v.isBlank()) {
                configs.put(key, v);
                return;
            }
        }
    }

    private void createIfMissing(AdminClient admin, Set<String> existing, String topicName, boolean compact)
            throws Exception {
        if (existing.contains(topicName)) {
            log.info("Kafka topic already exists: {}", topicName);
            return;
        }
        NewTopic topic = new NewTopic(topicName, properties.getTopicPartitions(), properties.getTopicReplicas());
        if (compact) {
            Map<String, String> cfg = new HashMap<>();
            cfg.put("cleanup.policy", "compact");
            cfg.put("min.cleanable.dirty.ratio", "0.1");
            cfg.put("segment.ms", "3600000");
            cfg.put("delete.retention.ms", "86400000");
            cfg.put("min.compaction.lag.ms", "0");
            topic.configs(cfg);
        }
        try {
            admin.createTopics(List.of(topic)).all().get(20, TimeUnit.SECONDS);
            log.info(
                    "Kafka topic created: {} (partitions={}, replicas={}, compact={})",
                    topicName,
                    properties.getTopicPartitions(),
                    properties.getTopicReplicas(),
                    compact);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                log.info("Kafka topic already exists (race): {}", topicName);
                return;
            }
            throw e;
        }
    }

    /** 已有 topic 也强制补上 compact（幂等 alter）。 */
    private void ensureCompactPolicy(AdminClient admin, String topicName) throws Exception {
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
        Collection<AlterConfigOp> ops = List.of(
                new AlterConfigOp(new ConfigEntry("cleanup.policy", "compact"), AlterConfigOp.OpType.SET),
                new AlterConfigOp(new ConfigEntry("min.cleanable.dirty.ratio", "0.1"), AlterConfigOp.OpType.SET),
                new AlterConfigOp(new ConfigEntry("delete.retention.ms", "86400000"), AlterConfigOp.OpType.SET));
        try {
            admin.incrementalAlterConfigs(Map.of(resource, ops)).all().get(20, TimeUnit.SECONDS);
            log.info("Kafka topic compact policy ensured: {}", topicName);
        } catch (ExecutionException e) {
            log.warn("Failed to alter compact policy on {}: {}", topicName, e.getMessage());
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
