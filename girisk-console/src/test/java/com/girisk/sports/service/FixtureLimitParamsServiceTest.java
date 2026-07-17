package com.girisk.sports.service;

import com.girisk.config.SportsRiskProperties;
import com.girisk.sports.dto.FixtureLimitOverrideRequest;
import com.girisk.sports.model.SportsMatch;
import com.girisk.sports.repository.SportsMatchRepository;
import com.girisk.sports.store.InMemoryFixtureLimitOverrideStore;
import com.girisk.sports.store.InMemoryScopeLimitOverrideStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixtureLimitParamsServiceTest {

    private FakeMatchRepository matchRepository;
    private FixtureLimitParamsService service;

    @BeforeEach
    void setUp() {
        matchRepository = new FakeMatchRepository();
        InMemoryFixtureLimitOverrideStore store = new InMemoryFixtureLimitOverrideStore();
        InMemoryScopeLimitOverrideStore scopeStore = new InMemoryScopeLimitOverrideStore();
        SportsRiskProperties props = new SportsRiskProperties();
        props.setDefaultDelta(0.2);
        props.setSeedPayoutYuan(2000);
        props.setMaxWorstLossYuan(1000);
        props.setMaxBetPayoutYuan(0);
        ScopeLimitParamsService scopeService = new ScopeLimitParamsService(scopeStore, store, props);
        ObjectProvider<StringRedisTemplate> redis = new ObjectProvider<>() {
            @Override
            public StringRedisTemplate getObject() {
                return null;
            }

            @Override
            public StringRedisTemplate getObject(Object... args) {
                return null;
            }

            @Override
            public StringRedisTemplate getIfAvailable() {
                return null;
            }

            @Override
            public StringRedisTemplate getIfUnique() {
                return null;
            }
        };
        service = new FixtureLimitParamsService(matchRepository, store, scopeService, props, redis);
    }

    @Test
    void resolveUsesMatchThresholdWhenNoOverride() {
        var p = service.resolve("germany-paraguay");
        assertEquals(0, new BigDecimal("0.2").compareTo(p.delta()));
        assertEquals(0, new BigDecimal("2000").compareTo(p.seedPayoutYuan()));
        assertEquals(0, new BigDecimal("200000").compareTo(p.maxWorstLossYuan()));
        assertFalse(p.overrideActive());
    }

    @Test
    void upsertAppliesOverrideAndSyncsDb() {
        service.upsert("germany-paraguay", new FixtureLimitOverrideRequest(
                new BigDecimal("0.15"),
                new BigDecimal("3000"),
                new BigDecimal("50000"),
                new BigDecimal("8000"),
                "desk-1"));

        var p = service.resolve("germany-paraguay");
        assertTrue(p.overrideActive());
        assertEquals(0, new BigDecimal("0.15").compareTo(p.delta()));
        assertEquals(0, new BigDecimal("3000").compareTo(p.seedPayoutYuan()));
        assertEquals(0, new BigDecimal("50000").compareTo(p.maxWorstLossYuan()));
        assertEquals(0, new BigDecimal("8000").compareTo(p.maxBetPayoutYuan()));
        assertEquals(0, new BigDecimal("50000").compareTo(matchRepository.lastThreshold.get()));
        assertEquals(0, new BigDecimal("0.15").compareTo(matchRepository.lastDelta.get()));
    }

    @Test
    void clearRemovesOverride() {
        service.upsert("germany-paraguay", new FixtureLimitOverrideRequest(
                new BigDecimal("0.1"), null, null, null, "desk-1"));
        assertTrue(service.resolve("germany-paraguay").overrideActive());
        service.clear("germany-paraguay");
        assertFalse(service.resolve("germany-paraguay").overrideActive());
    }

    /** Minimal stub — avoids Mockito agent attachment in sandboxed CI. */
    static final class FakeMatchRepository extends SportsMatchRepository {
        final AtomicReference<BigDecimal> lastThreshold = new AtomicReference<>();
        final AtomicReference<BigDecimal> lastDelta = new AtomicReference<>();
        private SportsMatch match = new SportsMatch(
                1L, "germany-paraguay", "Germany", "Paraguay",
                "football", "FRIENDLY", "国际友谊",
                new BigDecimal("200000"), false, BigDecimal.ZERO, new BigDecimal("0.2"),
                "ACTIVE", null, LocalDateTime.now(), LocalDateTime.now());

        FakeMatchRepository() {
            super(null);
        }

        @Override
        public Optional<SportsMatch> findByCode(String matchCode) {
            return "germany-paraguay".equals(matchCode) ? Optional.of(match) : Optional.empty();
        }

        @Override
        public List<SportsMatch> findAll() {
            return List.of(match);
        }

        @Override
        public void updateMeta(
                String matchCode, String home, String away,
                String sportCode, String leagueCode, String leagueName,
                BigDecimal threshold, BigDecimal delta) {
            lastThreshold.set(threshold);
            lastDelta.set(delta);
            match = new SportsMatch(
                    match.id(), matchCode, home, away, sportCode, leagueCode, leagueName,
                    threshold, match.limitMode(), match.currentExposure(), delta,
                    match.status(), match.lastCheckAt(), match.createdAt(), match.updatedAt());
        }
    }
}
