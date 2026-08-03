package com.girisk.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台 Doppler {@code INFRA_*_DB_SERVICE_URL} / {@code SPRING_DATASOURCE_URL}
 * 常见为 {@code postgresql://host:5432/db}，Spring JDBC / Hikari 需要 {@code jdbc:postgresql://...}。
 */
public class DatasourceUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE = "giriskDatasourceUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> props = new LinkedHashMap<>();

        String url = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_URL"),
                environment.getProperty("spring.datasource.url"),
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("JDBC_URL"),
                findInfraDbServiceUrl(environment));
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
            // 自定义键优先，避免 yaml 里 ${SPRING_DATASOURCE_URL} 再读到未规范化的系统环境变量
            props.put("girisk.datasource.jdbc-url", normalized);
            props.put("spring.datasource.url", normalized);
            props.put("SPRING_DATASOURCE_URL", normalized);
        }

        props.putAll(aliasUserPass(environment));
        if (!props.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, props));
        }
    }

    /** 扫描 Doppler 风格 INFRA_*_DB_SERVICE_URL。 */
    static String findInfraDbServiceUrl(ConfigurableEnvironment environment) {
        for (PropertySource<?> ps : environment.getPropertySources()) {
            if (!(ps instanceof EnumerablePropertySource<?> eps)) {
                continue;
            }
            for (String name : eps.getPropertyNames()) {
                if (name == null) {
                    continue;
                }
                String upper = name.toUpperCase().replace('.', '_').replace('-', '_');
                if (upper.endsWith("DB_SERVICE_URL") || upper.endsWith("_DATABASE_URL")) {
                    Object v = eps.getProperty(name);
                    if (v != null && !v.toString().isBlank()) {
                        return v.toString().trim();
                    }
                }
            }
        }
        Map<String, Object> sys = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            sys.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Object> e : sys.entrySet()) {
            String key = e.getKey();
            if (key != null && (key.endsWith("DB_SERVICE_URL") || key.endsWith("_DATABASE_URL"))) {
                Object v = e.getValue();
                if (v != null && !v.toString().isBlank()) {
                    return v.toString().trim();
                }
            }
        }
        return null;
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

    /**
     * {@code postgresql://} / {@code postgres://} → {@code jdbc:postgresql://}。
     * 已是 jdbc: 则原样返回。
     */
    public static String normalizeJdbcUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (trimmed.startsWith("jdbc:")) {
            return trimmed;
        }
        if (trimmed.startsWith("postgresql://")) {
            return "jdbc:" + trimmed;
        }
        if (trimmed.startsWith("postgres://")) {
            return "jdbc:postgresql://" + trimmed.substring("postgres://".length());
        }
        return trimmed;
    }

    @Override
    public int getOrder() {
        // 尽量早，但仍晚于系统环境变量加载
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
