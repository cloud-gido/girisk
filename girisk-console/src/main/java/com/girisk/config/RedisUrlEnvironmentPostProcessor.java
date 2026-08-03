package com.girisk.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可选：把解析结果提前写进 Environment，便于日志 / Actuator 看到离散项。
 * <p>
 * 真正建连以 {@link GiriskRedisConnectionConfiguration} 的 {@code RedisConnectionDetails} 为准。
 * 这里<strong>不再</strong>写入 {@code spring.data.redis.url}（避免 Boot URL 模式）。
 */
public class RedisUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE = "giriskRedisUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = RedisConnectionSupport.firstRedisUri(
                environment.getProperty("SPRING_DATA_REDIS_URL"),
                environment.getProperty("spring.data.redis.url"),
                environment.getProperty("REDIS_URL"),
                environment.getProperty("REDIS_HOST"),
                RedisConnectionSupport.findInfraRedisUrl(environment));
        if (url == null) {
            // 若平台塞了空 REDIS_HOST，清掉以免 yaml ${REDIS_HOST:localhost} 吃到空串
            String host = environment.getProperty("REDIS_HOST");
            if (host != null && host.isBlank()) {
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("REDIS_HOST", "localhost");
                props.put("spring.data.redis.host", "localhost");
                environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE + "BlankHost", props));
            }
            return;
        }
        try {
            RedisUrlParser.Info info = RedisConnectionSupport.resolve(environment);
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("spring.data.redis.host", info.host());
            props.put("spring.data.redis.port", info.port());
            props.put("spring.data.redis.database", info.database());
            props.put("spring.data.redis.ssl.enabled", info.ssl());
            props.put("REDIS_HOST", info.host());
            props.put("REDIS_PORT", String.valueOf(info.port()));
            if (info.password() != null && !info.password().isBlank()) {
                props.put("spring.data.redis.password", info.password());
            }
            // 显式不要设置 spring.data.redis.url
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, props));
        } catch (RuntimeException ignored) {
            // 建连阶段 GiriskRedisConnectionConfiguration 会给出明确错误
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
