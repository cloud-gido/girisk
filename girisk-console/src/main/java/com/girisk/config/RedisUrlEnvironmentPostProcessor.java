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
 * 将 Doppler / 环境变量中的 {@code rediss://} URL 展开为 Spring Data Redis 离散属性。
 * <p>
 * Spring 默认 {@code RedisURI} 解析遇密码特殊字符会失败；此处与 GISO 同策略手工拆解。
 * <p>
 * 注意：不可把 {@code spring.data.redis.url} 设成空串 —— Boot 会当成「有 URL」再解析，
 * 得到空 host 并抛 {@code Host must not be empty}。应写入无鉴权的 clean URL，密码走独立属性。
 */
public class RedisUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE = "giriskRedisUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = firstRedisUri(
                environment.getProperty("SPRING_DATA_REDIS_URL"),
                environment.getProperty("spring.data.redis.url"),
                environment.getProperty("REDIS_URL"),
                // 仅当 REDIS_HOST 本身是 redis(s):// URI 时才当连接串（平台偶发整段塞进 HOST）
                environment.getProperty("REDIS_HOST"),
                findInfraRedisUrl(environment));
        if (url == null) {
            return;
        }
        String overridePassword = firstNonBlank(
                environment.getProperty("SPRING_DATA_REDIS_PASSWORD"),
                environment.getProperty("spring.data.redis.password"),
                environment.getProperty("REDIS_PASSWORD"));
        RedisUrlParser.Info info = RedisUrlParser.parse(url, overridePassword);
        if (info.host() == null || info.host().isBlank()) {
            throw new IllegalStateException(
                    "Redis URL parsed empty host (check SPRING_DATA_REDIS_URL / REDIS_URL): "
                            + RedisUrlParser.redact(url));
        }

        // Boot 若看见带密码的原 URL 会再用 URI 解析并踩特殊字符；改为无鉴权 clean URL + 独立 password
        String cleanUrl = (info.ssl() ? "rediss://" : "redis://")
                + info.host() + ":" + info.port() + "/" + info.database();

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.data.redis.host", info.host());
        props.put("spring.data.redis.port", info.port());
        props.put("spring.data.redis.database", info.database());
        props.put("spring.data.redis.ssl.enabled", info.ssl());
        props.put("spring.data.redis.url", cleanUrl);
        props.put("SPRING_DATA_REDIS_URL", cleanUrl);
        props.put("REDIS_URL", cleanUrl);
        if (info.username() != null && !info.username().isBlank()) {
            props.put("spring.data.redis.username", info.username());
        }
        if (info.password() != null && !info.password().isBlank()) {
            props.put("spring.data.redis.password", info.password());
            props.put("SPRING_DATA_REDIS_PASSWORD", info.password());
            props.put("REDIS_PASSWORD", info.password());
        }
        props.put("REDIS_HOST", info.host());
        props.put("REDIS_PORT", String.valueOf(info.port()));

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, props));
    }

    /** 只返回真正的 redis(s):// 连接串。 */
    static String firstRedisUri(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && RedisUrlParser.isRedisUri(v.trim())) {
                return v.trim();
            }
        }
        return null;
    }

    static String findInfraRedisUrl(ConfigurableEnvironment environment) {
        for (PropertySource<?> ps : environment.getPropertySources()) {
            if (!(ps instanceof EnumerablePropertySource<?> eps)) {
                continue;
            }
            for (String name : eps.getPropertyNames()) {
                if (name == null) {
                    continue;
                }
                String upper = name.toUpperCase().replace('.', '_').replace('-', '_');
                if (upper.contains("REDIS") && (upper.endsWith("_URL") || upper.endsWith("_URI"))) {
                    Object v = eps.getProperty(name);
                    if (v != null && RedisUrlParser.isRedisUri(v.toString())) {
                        return v.toString().trim();
                    }
                }
            }
        }
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            String key = e.getKey();
            if (key != null && key.contains("REDIS") && (key.endsWith("_URL") || key.endsWith("_URI"))) {
                String v = e.getValue();
                if (v != null && RedisUrlParser.isRedisUri(v)) {
                    return v.trim();
                }
            }
        }
        return null;
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
