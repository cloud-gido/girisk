package com.girisk.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisUrlEnvironmentPostProcessorTest {

    @Test
    void expandsRedissUrlWithoutBlankingSpringRedisUrl() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(
                "SPRING_DATA_REDIS_URL",
                "rediss://:sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI@master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com/0");

        new RedisUrlEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertEquals(
                "master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com",
                env.getProperty("spring.data.redis.host"));
        assertEquals("6379", String.valueOf(env.getProperty("spring.data.redis.port")));
        assertEquals("true", String.valueOf(env.getProperty("spring.data.redis.ssl.enabled")));
        assertEquals("sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI", env.getProperty("spring.data.redis.password"));

        String url = env.getProperty("spring.data.redis.url");
        assertTrue(url != null && !url.isBlank(), "url must not be blanked");
        assertFalse(url.contains("sTtN"), "password must not remain in url");
        assertTrue(url.startsWith("rediss://master."));
    }

    @Test
    void ignoresPlainHostnameInRedisHost() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("REDIS_HOST", "127.0.0.1");
        env.setProperty("REDIS_PORT", "6379");

        new RedisUrlEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());

        // 未注入 giriskRedisUrl 源时，host 仍是原值（EPP no-op）
        assertEquals("127.0.0.1", env.getProperty("REDIS_HOST"));
        assertEquals(null, env.getPropertySources().get(RedisUrlEnvironmentPostProcessor.PROPERTY_SOURCE));
    }
}
