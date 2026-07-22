package com.girisk.config;

import com.girisk.event.repository.RiskEventRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "girisk.kafka.enabled", havingValue = "true")
public class KafkaStreamConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaStreamConfig.class);

    @Bean
    ProducerFactory<String, String> producerFactory(RiskKafkaProperties props) {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.ACKS_CONFIG, "all");
        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configs.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        configs.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        configs.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
        configs.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);
        configs.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        configs.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        KafkaSaslSupport.applyFromEnv(configs);
        return new DefaultKafkaProducerFactory<>(configs);
    }

    @Bean
    KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    ConsumerFactory<String, String> consumerFactory(RiskKafkaProperties props) {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, props.getConsumerGroup());
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        // 抗踢：小批量 + 拉长 max.poll.interval，给 DB/瞬时抖动留余量
        configs.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Math.max(1, props.getConsumerMaxPollRecords()));
        configs.put(
                ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,
                Math.max(60_000, props.getConsumerMaxPollIntervalMs()));
        configs.put(
                ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,
                Math.max(10_000, props.getConsumerSessionTimeoutMs()));
        configs.put(
                ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG,
                Math.max(1_000, props.getConsumerHeartbeatIntervalMs()));
        KafkaSaslSupport.applyFromEnv(configs);
        return new DefaultKafkaConsumerFactory<>(configs);
    }

    @Bean
    CommonErrorHandler kafkaCommonErrorHandler(
            RiskKafkaProperties props, ObjectProvider<RiskEventRepository> eventRepository) {
        long attempts = Math.max(1, props.getDecisionIngestMaxAttempts());
        // 固定间隔重试；耗尽后跳过该条并记 DECISION_INGEST_DEAD，避免毒消息卡死整组
        return new DefaultErrorHandler(
                (record, ex) -> {
                    log.error(
                            "Kafka record skipped after retries topic={} partition={} offset={}: {}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            ex.getMessage());
                    RiskEventRepository repo = eventRepository.getIfAvailable();
                    if (repo == null) {
                        return;
                    }
                    try {
                        String raw = record.value() == null
                                ? ""
                                : record.value().toString();
                        if (raw.length() > 400) {
                            raw = raw.substring(0, 400);
                        }
                        repo.insert(
                                "DECISION_INGEST_DEAD",
                                "ERROR",
                                null,
                                null,
                                "决策入库重试耗尽已跳过",
                                "topic=" + record.topic()
                                        + " partition=" + record.partition()
                                        + " offset=" + record.offset()
                                        + " err=" + ex.getMessage()
                                        + " raw=" + raw);
                    } catch (Exception e) {
                        log.warn("DECISION_INGEST_DEAD event failed: {}", e.getMessage());
                    }
                },
                new FixedBackOff(1_000L, attempts - 1));
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, CommonErrorHandler kafkaCommonErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaCommonErrorHandler);
        return factory;
    }
}
