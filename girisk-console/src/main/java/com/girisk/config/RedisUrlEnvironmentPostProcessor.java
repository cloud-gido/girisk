package com.girisk.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 Doppler / 环境变量中的 {@code rediss://} URL 展开为 Spring Data Redis 离散属性。
 * <p>
 * Spring 默认 {@code RedisURI} 解析遇密码特殊字符会失败；此处与 GISO 同策略手工拆解。
 */
public class RedisUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE = "giriskRedisUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = firstNonBlank(
                environment.getProperty("SPRING_DATA_REDIS_URL"),
                environment.getProperty("spring.data.redis.url"),
                environment.getProperty("REDIS_URL"),
                environment.getProperty("REDIS_HOST"));
        if (!RedisUrlParser.isRedisUri(url)) {
            return;
        }
        String overridePassword = firstNonBlank(
                environment.getProperty("SPRING_DATA_REDIS_PASSWORD"),
                environment.getProperty("spring.data.redis.password"),
                environment.getProperty("REDIS_PASSWORD"));
        RedisUrlParser.Info info = RedisUrlParser.parse(url, overridePassword);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.data.redis.host", info.host());
        props.put("spring.data.redis.port", info.port());
        props.put("spring.data.redis.database", info.database());
        props.put("spring.data.redis.ssl.enabled", info.ssl());
        if (info.username() != null && !info.username().isBlank()) {
            props.put("spring.data.redis.username", info.username());
        }
        if (info.password() != null && !info.password().isBlank()) {
            props.put("spring.data.redis.password", info.password());
        }
        // 避免 Boot 再用 java.net.URI 二次解析整段 URL
        props.put("spring.data.redis.url", "");
        // 覆盖可能误把整段 URL 当成 host 的 REDIS_HOST
        props.put("REDIS_HOST", info.host());
        props.put("REDIS_PORT", String.valueOf(info.port()));

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, props));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
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
