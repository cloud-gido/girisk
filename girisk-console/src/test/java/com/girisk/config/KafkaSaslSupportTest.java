package com.girisk.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaSaslSupportTest {

    @Test
    void escapesQuotesInJaas() {
        assertEquals("p@ss\\\"word", KafkaSaslSupport.jaasEscape("p@ss\"word"));
    }

    @Test
    void doesNotOverwriteExistingJaas() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("sasl.jaas.config", "already-set");
        KafkaSaslSupport.applyFromEnv(configs);
        assertEquals("already-set", configs.get("sasl.jaas.config"));
    }

    @Test
    void buildsJaasWhenEnvPresent() {
        // 无 env 时 skip；有 env 的 CI/本地用 System 环境验证形态
        String user = System.getenv("KAFKA_SASL_USERNAME");
        String pass = System.getenv("KAFKA_SASL_PASSWORD");
        if (user == null || pass == null || user.isBlank() || pass.isBlank()) {
            return;
        }
        Map<String, Object> configs = new HashMap<>();
        KafkaSaslSupport.applyFromEnv(configs);
        assertTrue(configs.get("sasl.jaas.config").toString().contains("username=\"" + user + "\""));
        assertEquals("SASL_SSL", configs.get("security.protocol"));
        assertEquals("SCRAM-SHA-512", configs.get("sasl.mechanism"));
    }
}
