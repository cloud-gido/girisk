package com.girisk.sports.service;

import com.girisk.sports.dto.ScopeGateOverrideRequest;
import com.girisk.sports.model.LimitScopeType;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeGateServiceTest {

    private ScopeGateService service;
    private FakeMatchRepository matches;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        matches = new FakeMatchRepository();
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
                        new InMemoryScopeLimitOverrideStore(),
                        new InMemoryFixtureLimitOverrideStore(),
                        noPublisher,
                        emptyOutbox());
        service = new ScopeGateService(
                new InMemoryScopeGateOverrideStore(), matches, new ScopeDutyAuth(), dispatch);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveDefaultsAllOn() {
        var g = service.resolveForMatch(matches.findByCode("M1").orElseThrow());
        assertTrue(g.tradingEnabled());
        assertTrue(g.limitGateEnabled());
        assertTrue(g.exposureGateEnabled());
        assertEquals("DEFAULT", g.limitGateSource());
    }

    @Test
    void matchOverridesLeague() {
        service.upsert(LimitScopeType.LEAGUE, "football:L1",
                new ScopeGateOverrideRequest(true, false, true, "admin"));
        service.upsert(LimitScopeType.MATCH, "M1",
                new ScopeGateOverrideRequest(true, true, false, "admin"));
        var g = service.resolveForMatch(matches.findByCode("M1").orElseThrow());
        assertTrue(g.limitGateEnabled());
        assertEquals("MATCH", g.limitGateSource());
        assertFalse(g.exposureGateEnabled());
        assertEquals("MATCH", g.exposureGateSource());
    }

    @Test
    void suspendedMatchForcesTradingOff() {
        matches.suspended = true;
        var g = service.resolveForMatch(matches.findByCode("M1").orElseThrow());
        assertFalse(g.tradingEnabled());
        assertEquals("MATCH_STATUS", g.tradingSource());
    }

    @Test
    void sportInheritsToMatch() {
        service.upsert(LimitScopeType.SPORT, "football",
                new ScopeGateOverrideRequest(true, true, false, "admin"));
        var g = service.resolveForMatch(matches.findByCode("M1").orElseThrow());
        assertFalse(g.exposureGateEnabled());
        assertEquals("SPORT", g.exposureGateSource());
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

    static final class FakeMatchRepository extends SportsMatchRepository {
        boolean suspended;

        FakeMatchRepository() {
            super(null);
        }

        @Override
        public Optional<SportsMatch> findByCode(String matchCode) {
            return Optional.of(new SportsMatch(
                    1L, matchCode, "A", "B", "football", "L1", "联赛1",
                    new BigDecimal("1000"), false, BigDecimal.ZERO, new BigDecimal("0.2"),
                    suspended ? "SUSPENDED" : "ACTIVE",
                    null, LocalDateTime.now(), LocalDateTime.now()));
        }

        @Override
        public void updateStatus(String matchCode, String status) {
            suspended = "SUSPENDED".equals(status);
        }

        @Override
        public int updateStatusAll(String status) {
            return 0;
        }

        @Override
        public int updateStatusBySport(String sportCode, String status) {
            return 0;
        }

        @Override
        public int updateStatusByLeague(String sportCode, String leagueCode, String status) {
            return 0;
        }
    }
}
