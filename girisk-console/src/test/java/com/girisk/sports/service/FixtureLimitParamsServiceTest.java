package com.girisk.sports.service;

import com.girisk.config.SportsRiskProperties;
import com.girisk.sports.dto.FixtureLimitOverrideRequest;
import com.girisk.sports.model.MatchLimitSegment;
import com.girisk.sports.model.SportsMatch;
import com.girisk.sports.repository.SportsMatchRepository;
import com.girisk.sports.outbox.ScopeRiskConfigOutbox;
import com.girisk.sports.store.InMemoryFixtureLimitOverrideStore;
import com.girisk.sports.store.InMemoryScopeGateOverrideStore;
import com.girisk.sports.store.InMemoryScopeLimitOverrideStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        matchRepository = new FakeMatchRepository();
        InMemoryFixtureLimitOverrideStore store = new InMemoryFixtureLimitOverrideStore();
        InMemoryScopeLimitOverrideStore scopeStore = new InMemoryScopeLimitOverrideStore();
        SportsRiskProperties props = new SportsRiskProperties();
        props.setDefaultDelta(0.2);
        props.setSeedPayoutYuan(5000);
        props.setMaxWorstLossYuan(200000);
        props.setMaxBetPayoutYuan(0);
        ScopeDutyAuth dutyAuth = new ScopeDutyAuth();
        ObjectProvider<com.girisk.flink.ScopeRiskConfigKafkaPublisher> noPublisher =
                new ObjectProvider<>() {
                    @Override
                    public com.girisk.flink.ScopeRiskConfigKafkaPublisher getObject() {
                        return null;
                    }

                    @Override
                    public com.girisk.flink.ScopeRiskConfigKafkaPublisher getObject(Object... args) {
                        return null;
                    }

                    @Override
                    public com.girisk.flink.ScopeRiskConfigKafkaPublisher getIfAvailable() {
                        return null;
                    }

                    @Override
                    public com.girisk.flink.ScopeRiskConfigKafkaPublisher getIfUnique() {
                        return null;
                    }
                };
        ScopeRiskConfigDispatchService dispatch =
                new ScopeRiskConfigDispatchService(
                        new InMemoryScopeGateOverrideStore(),
                        scopeStore,
                        store,
                        noPublisher,
                        emptyOutbox());
        ScopeLimitParamsService scopeService =
                new ScopeLimitParamsService(scopeStore, store, props, dutyAuth, dispatch);
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
        service = new FixtureLimitParamsService(
                matchRepository, store, scopeService, props, dutyAuth, dispatch, redis);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveUsesPropertiesWhenNoOverride() {
        var p = service.resolve("germany-paraguay");
        assertEquals(0, new BigDecimal("0.2").compareTo(p.delta()));
        assertEquals(0, new BigDecimal("5000").compareTo(p.seedPayoutYuan()));
        // 无显式覆盖时走 properties，不读 sports_match.exposure_threshold
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

    @Test
    void preSegmentOverridesMatchOnlyForPre() {
        service.upsert("germany-paraguay", new FixtureLimitOverrideRequest(
                null, null, new BigDecimal("5000"), null, "desk-1"));
        service.upsert(
                "germany-paraguay",
                new FixtureLimitOverrideRequest(null, null, new BigDecimal("2000"), null, "desk-1"),
                MatchLimitSegment.PRE);

        var all = service.resolve("germany-paraguay", MatchLimitSegment.ALL);
        var pre = service.resolve("germany-paraguay", MatchLimitSegment.PRE);
        var live = service.resolve("germany-paraguay", MatchLimitSegment.LIVE);

        assertEquals(0, new BigDecimal("5000").compareTo(all.maxWorstLossYuan()));
        assertEquals(0, new BigDecimal("2000").compareTo(pre.maxWorstLossYuan()));
        assertEquals(0, new BigDecimal("5000").compareTo(live.maxWorstLossYuan()));
        assertTrue(pre.overrideActive());
        assertFalse(live.overrideActive());
    }

    @Test
    void segmentInheritsWhenNoPreLiveOverride() {
        // 无段覆盖时 ALL/PRE/LIVE 同源（properties），不得因库内 exposure_threshold 分叉
        var all = service.resolve("germany-paraguay", MatchLimitSegment.ALL);
        var pre = service.resolve("germany-paraguay", MatchLimitSegment.PRE);
        var live = service.resolve("germany-paraguay", MatchLimitSegment.LIVE);
        assertEquals(0, new BigDecimal("200000").compareTo(all.maxWorstLossYuan()));
        assertEquals(0, all.maxWorstLossYuan().compareTo(pre.maxWorstLossYuan()));
        assertEquals(0, all.maxWorstLossYuan().compareTo(live.maxWorstLossYuan()));
        assertEquals(0, new BigDecimal("5000").compareTo(all.seedPayoutYuan()));
        assertFalse(pre.overrideActive());
        assertFalse(live.overrideActive());
    }

    private static ObjectProvider<ScopeRiskConfigOutbox> emptyOutbox() {
        return new ObjectProvider<>() {
            @Override
            public ScopeRiskConfigOutbox getObject() {
                return null;
            }

            @Override
            public ScopeRiskConfigOutbox getObject(Object... args) {
                return null;
            }

            @Override
            public ScopeRiskConfigOutbox getIfAvailable() {
                return null;
            }

            @Override
            public ScopeRiskConfigOutbox getIfUnique() {
                return null;
            }
        };
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
