package com.girisk.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisConnectionSupportTest {

    @Test
    void resolvesRedissUrlWithSpecialPassword() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(
                "SPRING_DATA_REDIS_URL",
                "rediss://:sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI@master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com/0");
        env.setProperty("REDIS_HOST", ""); // 平台空串不应干扰 URL

        RedisUrlParser.Info info = RedisConnectionSupport.resolve(env);
        assertEquals("master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com", info.host());
        assertEquals(6379, info.port());
        assertTrue(info.ssl());
        assertEquals("sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI", info.password());
    }

    @Test
    void blankRedisHostFallsBackToLocalhostWhenNoUrl() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("REDIS_HOST", "");
        env.setProperty("REDIS_PORT", "6379");

        RedisUrlParser.Info info = RedisConnectionSupport.resolve(env);
        assertEquals("localhost", info.host());
    }
}
