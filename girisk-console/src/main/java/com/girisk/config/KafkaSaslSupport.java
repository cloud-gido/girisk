package com.girisk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 将 MSK SCRAM 凭据写入 Kafka client {@link Map}（与 GISO {@code applyKafkaSaslFromEnv} 同模式）。
 * <p>
 * Console 的 {@code KafkaStreamConfig} 自建 Consumer/Producer factory，
 * <strong>不会</strong>自动继承 {@code spring.kafka.properties.*}，必须在此显式注入。
 */
public final class KafkaSaslSupport {

    private static final Logger log = LoggerFactory.getLogger(KafkaSaslSupport.class);

    private KafkaSaslSupport() {}

    /** 若存在用户名/密码，写入 security.protocol / sasl.*；已有 jaas 则不覆盖。 */
    public static void applyFromEnv(Map<String, Object> configs) {
        if (configs == null) {
            return;
        }
        if (configs.containsKey("sasl.jaas.config")
                && configs.get("sasl.jaas.config") != null
                && !configs.get("sasl.jaas.config").toString().isBlank()) {
            return;
        }

        String user = firstNonBlank(
                System.getenv("KAFKA_SASL_USERNAME"),
                System.getenv("GISO_KAFKA_SASL_USERNAME"),
                System.getenv("INFRA_KAFKA_SASL_USERNAME"));
        String pass = firstNonBlank(
                System.getenv("KAFKA_SASL_PASSWORD"),
                System.getenv("GISO_KAFKA_SASL_PASSWORD"),
                System.getenv("INFRA_KAFKA_SASL_PASSWORD"));
        if (user == null || pass == null) {
            return;
        }

        configs.putIfAbsent("security.protocol", "SASL_SSL");
        configs.putIfAbsent("sasl.mechanism", "SCRAM-SHA-512");
        configs.put(
                "sasl.jaas.config",
                "org.apache.kafka.common.security.scram.ScramLoginModule required "
                        + "username=\"" + jaasEscape(user) + "\" "
                        + "password=\"" + jaasEscape(pass) + "\";");
        log.info(
                "GiRisk Kafka SASL ready → protocol={} mechanism={} user={}",
                configs.get("security.protocol"),
                configs.get("sasl.mechanism"),
                user);
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
}
