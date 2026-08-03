package com.girisk.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisUrlParserTest {

    @Test
    void parsesRedissWithSpecialPasswordChars() {
        RedisUrlParser.Info info = RedisUrlParser.parse(
                "rediss://:sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI@master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com/0",
                null);
        assertEquals("rediss", info.scheme());
        assertTrue(info.ssl());
        assertEquals("master.gamelinelab-dev-sharedcache.cddsor.sae1.cache.amazonaws.com", info.host());
        assertEquals(6379, info.port());
        assertEquals(0, info.database());
        assertEquals("sTtN?Yo5q-qaHGpP6=kEWJRT!WOTFPI", info.password());
        assertEquals("", info.username());
    }

    @Test
    void overridePasswordWhenUrlHasNoAuth() {
        RedisUrlParser.Info info = RedisUrlParser.parse(
                "rediss://master.cache.amazonaws.com:6379/0",
                "secret-from-env");
        assertEquals("secret-from-env", info.password());
        assertEquals(0, info.database());
    }

    @Test
    void redactsCredentials() {
        String redacted = RedisUrlParser.redact("rediss://:token@host.example/0");
        assertEquals("rediss://***@host.example/0", redacted);
    }
}
