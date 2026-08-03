package com.girisk.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaSaslEnvironmentPostProcessorTest {

    @Test
    void buildsRealJaasFromUsernamePassword() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("KAFKA_SASL_USERNAME", "msk-user");
        env.setProperty("KAFKA_SASL_PASSWORD", "p@ss\"word");

        new KafkaSaslEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());

        String jaas = env.getProperty("spring.kafka.properties.sasl.jaas.config");
        assertTrue(jaas != null && jaas.contains("username=\"msk-user\""));
        assertTrue(jaas.contains("password=\"p@ss\\\"word\""));
        assertFalse(jaas.contains("${KAFKA_SASL"));
    }
}
