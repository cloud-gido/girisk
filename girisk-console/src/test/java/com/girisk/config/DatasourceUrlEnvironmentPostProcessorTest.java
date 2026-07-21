package com.girisk.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DatasourceUrlEnvironmentPostProcessorTest {

    @Test
    void normalizeAddsJdbcPrefix() {
        assertEquals(
                "jdbc:postgresql://host:5432/girisk",
                DatasourceUrlEnvironmentPostProcessor.normalizeJdbcUrl(
                        "postgresql://host:5432/girisk"));
        assertEquals(
                "jdbc:postgresql://host:5432/girisk",
                DatasourceUrlEnvironmentPostProcessor.normalizeJdbcUrl(
                        "postgres://host:5432/girisk"));
        assertEquals(
                "jdbc:postgresql://host:5432/girisk",
                DatasourceUrlEnvironmentPostProcessor.normalizeJdbcUrl(
                        "jdbc:postgresql://host:5432/girisk"));
        assertNull(DatasourceUrlEnvironmentPostProcessor.normalizeJdbcUrl(null));
    }

    @Test
    void postProcessRewritesSpringDatasourceUrl() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(
                "SPRING_DATASOURCE_URL",
                "postgresql://gamelinelab-dev-core.example:5432/girisk");

        new DatasourceUrlEnvironmentPostProcessor()
                .postProcessEnvironment(env, new SpringApplication());

        assertEquals(
                "jdbc:postgresql://gamelinelab-dev-core.example:5432/girisk",
                env.getProperty("spring.datasource.url"));
        assertEquals(
                "jdbc:postgresql://gamelinelab-dev-core.example:5432/girisk",
                env.getProperty("girisk.datasource.jdbc-url"));
        assertEquals(
                "jdbc:postgresql://gamelinelab-dev-core.example:5432/girisk",
                env.getProperty("SPRING_DATASOURCE_URL"));
    }
}
