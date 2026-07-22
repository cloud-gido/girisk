package com.girisk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * GISO 同构：解析结果直接变成 {@link RedisConnectionDetails}，不让 Boot 再解析 {@code spring.data.redis.url}。
 * <p>
 * 避免：
 * <ul>
 *   <li>{@code REDIS_HOST=} 空串覆盖 {@code localhost} 默认值</li>
 *   <li>密码特殊字符导致 {@code java.net.URI} 解析失败 / 空 host</li>
 *   <li>EPP 把 url 写成 {@code ""} 触发 URL 模式</li>
 * </ul>
 */
@Configuration
@ConditionalOnClass(RedisConnectionDetails.class)
@ConditionalOnProperty(name = "girisk.redis.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(RedisAutoConfiguration.class)
public class GiriskRedisConnectionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GiriskRedisConnectionConfiguration.class);

    @Bean
    @Order(0)
    @ConditionalOnMissingBean(RedisConnectionDetails.class)
    RedisConnectionDetails giriskRedisConnectionDetails(Environment environment, RedisProperties redisProperties) {
        RedisUrlParser.Info info = RedisConnectionSupport.resolve(environment);

        // 同步给仍读 RedisProperties 的路径；清空 url，禁止 Boot 走 URI 二次解析
        redisProperties.setUrl(null);
        redisProperties.setHost(info.host());
        redisProperties.setPort(info.port());
        redisProperties.setDatabase(info.database());
        if (info.username() != null && !info.username().isBlank()) {
            redisProperties.setUsername(info.username());
        } else {
            redisProperties.setUsername(null);
        }
        if (info.password() != null && !info.password().isBlank()) {
            redisProperties.setPassword(info.password());
        }
        redisProperties.getSsl().setEnabled(info.ssl());

        log.info(
                "GiRisk Redis ready → {}://{}:{}/{} ssl={} auth={}",
                info.scheme(),
                info.host(),
                info.port(),
                info.database(),
                info.ssl(),
                info.password() == null || info.password().isBlank() ? "none" : "password");

        final String host = info.host();
        final int port = info.port();
        final int database = info.database();
        final String username = info.username();
        final String password = info.password();

        return new RedisConnectionDetails() {
            @Override
            public String getUsername() {
                return blankToNull(username);
            }

            @Override
            public String getPassword() {
                return blankToNull(password);
            }

            @Override
            public Standalone getStandalone() {
                return Standalone.of(host, port, database);
            }
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
