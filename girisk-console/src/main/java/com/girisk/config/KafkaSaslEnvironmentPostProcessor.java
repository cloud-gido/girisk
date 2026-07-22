package com.girisk.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对齐 GISO：用 {@code KAFKA_SASL_USERNAME}/{@code KAFKA_SASL_PASSWORD} 组装真实 JAAS。
 * <p>
 * K8s 里写 {@code username="${KAFKA_SASL_USERNAME}"} <strong>不会</strong>被 Kafka 客户端展开，
 * 会导致 MSK 认证失败、读包异常，甚至 {@code OutOfMemoryError}（误解析帧长度）。
 */
public class KafkaSaslEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE = "giriskKafkaSasl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String user = firstNonBlank(
                environment.getProperty("KAFKA_SASL_USERNAME"),
                environment.getProperty("SPRING_KAFKA_PROPERTIES_SASL_JAAS_USERNAME"));
        String pass = firstNonBlank(
                environment.getProperty("KAFKA_SASL_PASSWORD"),
                environment.getProperty("SPRING_KAFKA_PROPERTIES_SASL_JAAS_PASSWORD"));
        if (user == null || pass == null) {
            return;
        }

        String jaas = "org.apache.kafka.common.security.scram.ScramLoginModule required "
                + "username=\"" + jaasEscape(user) + "\" "
                + "password=\"" + jaasEscape(pass) + "\";";

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.kafka.properties.security.protocol", "SASL_SSL");
        props.put("spring.kafka.properties.sasl.mechanism", "SCRAM-SHA-512");
        props.put("spring.kafka.properties.sasl.jaas.config", jaas);

        props.put("spring.kafka.consumer.properties.security.protocol", "SASL_SSL");
        props.put("spring.kafka.consumer.properties.sasl.mechanism", "SCRAM-SHA-512");
        props.put("spring.kafka.consumer.properties.sasl.jaas.config", jaas);

        props.put("spring.kafka.producer.properties.security.protocol", "SASL_SSL");
        props.put("spring.kafka.producer.properties.sasl.mechanism", "SCRAM-SHA-512");
        props.put("spring.kafka.producer.properties.sasl.jaas.config", jaas);

        props.put("spring.kafka.admin.properties.security.protocol", "SASL_SSL");
        props.put("spring.kafka.admin.properties.sasl.mechanism", "SCRAM-SHA-512");
        props.put("spring.kafka.admin.properties.sasl.jaas.config", jaas);

        // 供 KafkaTopicBootstrap AdminClient 使用
        props.put("girisk.kafka.sasl.jaas.config", jaas);
        props.put("girisk.kafka.security.protocol", "SASL_SSL");
        props.put("girisk.kafka.sasl.mechanism", "SCRAM-SHA-512");

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, props));
    }

    static String jaasEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }
}
