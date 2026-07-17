package com.girisk.flink.risk.kafka;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;

import java.util.Locale;
import java.util.Properties;

/** 单行 UTF-8 JSON → 固定 topic。 */
public final class FootballOrderKafkaStringSink {

    private static final String PREFIX_BASE = "flink-football";

    private FootballOrderKafkaStringSink() {}

    public static KafkaSink<String> utf8Lines(String bootstrap, String topic, Properties kafkaClientProps) {
        var builder =
                KafkaSink.<String>builder()
                        .setBootstrapServers(bootstrap)
                        .setTransactionalIdPrefix(transactionalIdPrefix(topic))
                        .setRecordSerializer(
                                KafkaRecordSerializationSchema.<String>builder()
                                        .setTopic(topic)
                                        .setValueSerializationSchema(new SimpleStringSchema())
                                        .build())
                        .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE);
        if (kafkaClientProps != null && !kafkaClientProps.isEmpty()) {
            builder.setKafkaProducerConfig(kafkaClientProps);
        }
        return builder.build();
    }

    /**
     * 同一作业多个 Kafka Sink 时，checkpoint 下 transactionalIdPrefix 必须唯一（Flink Kafka 4.x）。
     *
     * <p>按 topic 派生，满足 Kafka transactional.id 字符约束。
     */
    static String transactionalIdPrefix(String topic) {
        String safe =
                topic == null || topic.isBlank()
                        ? "default"
                        : topic.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        return PREFIX_BASE + "-" + safe;
    }
}
