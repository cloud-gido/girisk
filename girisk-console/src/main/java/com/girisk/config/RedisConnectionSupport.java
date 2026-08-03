package com.girisk.config;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.Map;

/**
 * 从环境解析 Redis 连接（对齐 GISO {@code GatewayConfig.resolveDebugRedisUrl}）。
 * <p>
 * 优先级：完整 {@code redis(s)://} URL → 非空 HOST + PASSWORD 离散配置 → localhost。
 * 空串 HOST（平台常见 {@code REDIS_HOST=}）视为未设置，不会覆盖默认值。
 */
public final class RedisConnectionSupport {

    private RedisConnectionSupport() {
    }

    public static RedisUrlParser.Info resolve(Environment environment) {
        // 平台常把 INFRA_ARCHERY_REDIS_HOST 同步成 SPRING_DATA_REDIS_URL：
        // 既可能是 rediss:// 整段，也可能只是短主机名（与 GISO Archery 复用模式一致）
        String urlOrHost = firstNonBlank(
                environment.getProperty("SPRING_DATA_REDIS_URL"),
                environment.getProperty("spring.data.redis.url"),
                environment.getProperty("REDIS_URL"),
                findInfraRedisUrl(environment),
                environment.getProperty("REDIS_HOST"));
        String overridePassword = firstNonBlank(
                environment.getProperty("SPRING_DATA_REDIS_PASSWORD"),
                environment.getProperty("spring.data.redis.password"),
                environment.getProperty("REDIS_PASSWORD"));

        String url = null;
        if (RedisUrlParser.isRedisUri(urlOrHost)) {
            url = urlOrHost;
        }

        if (url != null) {
            RedisUrlParser.Info info = RedisUrlParser.parse(url, overridePassword);
            requireHost(info, url);
            return info;
        }

        // 非 URI：把 SPRING_DATA_REDIS_URL / REDIS_HOST 当主机名（GISO fromParts 路径）
        String host = firstNonBlank(
                RedisUrlParser.isRedisUri(urlOrHost) ? null : urlOrHost,
                environment.getProperty("REDIS_HOST"),
                environment.getProperty("spring.data.redis.host"));
        if (host == null || "localhost".equalsIgnoreCase(host)) {
            // spring 默认 localhost：若密码/URL 都没有，仍允许本地联调
            host = firstNonBlank(host, "localhost");
        }
        int port = parsePort(firstNonBlank(
                environment.getProperty("REDIS_PORT"),
                environment.getProperty("spring.data.redis.port")), 6379);
        int database = parsePort(firstNonBlank(
                environment.getProperty("REDIS_DB"),
                environment.getProperty("spring.data.redis.database")), 0);
        String username = firstNonBlank(
                environment.getProperty("SPRING_DATA_REDIS_USERNAME"),
                environment.getProperty("spring.data.redis.username"));
        boolean ssl = Boolean.parseBoolean(firstNonBlank(
                environment.getProperty("SPRING_DATA_REDIS_SSL"),
                environment.getProperty("spring.data.redis.ssl.enabled"),
                "false"));
        String scheme = ssl || (host != null && host.contains(".amazonaws.com")) ? "rediss" : "redis";
        RedisUrlParser.Info info = new RedisUrlParser.Info(
                scheme,
                host,
                port,
                username == null ? "" : username,
                overridePassword == null ? "" : overridePassword,
                database);
        if (host != null && host.contains(".amazonaws.com")) {
            info = new RedisUrlParser.Info("rediss", host, port, "", info.password(), 0);
        }
        requireHost(info, "host=" + host);
        return info;
    }

    private static void requireHost(RedisUrlParser.Info info, String source) {
        if (info.host() == null || info.host().isBlank()) {
            throw new IllegalStateException(
                    "Redis host is empty (source=" + RedisUrlParser.redact(source)
                            + "). Set SPRING_DATA_REDIS_URL=rediss://:token@host/0 "
                            + "or a non-empty REDIS_HOST. Empty REDIS_HOST=\"\" does not fall back to localhost.");
        }
    }

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

    static String findInfraRedisUrl(Environment environment) {
        if (environment instanceof ConfigurableEnvironment configurable) {
            for (PropertySource<?> ps : configurable.getPropertySources()) {
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

    static String firstNonBlank(String... values) {
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

    private static int parsePort(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
