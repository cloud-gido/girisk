package com.girisk.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台 Doppler {@code INFRA_*_DB_SERVICE_URL} 常见为 {@code postgresql://host:5432/db}，
 * Spring JDBC 需要 {@code jdbc:postgresql://...}。
 */
public class DatasourceUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE = "giriskDatasourceUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> props = new LinkedHashMap<>();

        String url = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_URL"),
                environment.getProperty("spring.datasource.url"));
        if (url != null && looksLikePlaceholderDefault(url)) {
            url = null;
        }
        if (url == null || url.isBlank()) {
            String host = firstNonBlank(environment.getProperty("POSTGRES_HOST"));
            if (host != null) {
                String port = firstNonBlank(environment.getProperty("POSTGRES_PORT"), "5432");
                String db = firstNonBlank(environment.getProperty("POSTGRES_DB"), "girisk");
                url = "jdbc:postgresql://" + host + ":" + port + "/" + db;
            }
        }
        if (url != null && !url.isBlank()) {
            String normalized = normalizeJdbcUrl(url);
            props.put("spring.datasource.url", normalized);
            props.put("SPRING_DATASOURCE_URL", normalized);
        }

        props.putAll(aliasUserPass(environment));
        if (!props.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, props));
        }
    }

    /** application-postgres 里未解析的嵌套默认值，视为无效。 */
    private static boolean looksLikePlaceholderDefault(String url) {
        return url.contains("${");
    }

    private static Map<String, Object> aliasUserPass(ConfigurableEnvironment environment) {
        Map<String, Object> props = new LinkedHashMap<>();
        String user = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_USERNAME"),
                environment.getProperty("POSTGRES_USER"));
        String pass = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_PASSWORD"),
                environment.getProperty("POSTGRES_PASSWORD"));
        if (user != null) {
            props.put("spring.datasource.username", user);
            props.put("SPRING_DATASOURCE_USERNAME", user);
        }
        if (pass != null) {
            props.put("spring.datasource.password", pass);
            props.put("SPRING_DATASOURCE_PASSWORD", pass);
        }
        return props;
    }

    static String normalizeJdbcUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.startsWith("jdbc:")) {
            return trimmed;
        }
        if (trimmed.startsWith("postgresql://") || trimmed.startsWith("postgres://")) {
            return "jdbc:" + trimmed.replaceFirst("^postgres://", "postgresql://");
        }
        return trimmed;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
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
