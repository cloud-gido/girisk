package com.girisk.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RedisUrlEnvironmentPostProcessorTest {

    @Test
    void doesNotSetSpringDataRedisUrl() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(
                "SPRING_DATA_REDIS_URL",
                "rediss://:token@master.cache.amazonaws.com/0");

        new RedisUrlEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertEquals("master.cache.amazonaws.com", env.getProperty("spring.data.redis.host"));
        // 我们的 property source 不应再写入 url；原 env 里的 URL 键仍在，但建连不靠它
        assertNull(env.getPropertySources().get(RedisUrlEnvironmentPostProcessor.PROPERTY_SOURCE)
                .getProperty("spring.data.redis.url"));
    }

    @Test
    void blankRedisHostRewrittenToLocalhost() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("REDIS_HOST", "  ");

        new RedisUrlEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertEquals("localhost", env.getProperty("REDIS_HOST"));
    }
}
