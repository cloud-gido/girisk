package com.girisk.sports.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.audit.OpsAuditService;
import com.girisk.auth.OperatorScopeService;
import com.girisk.common.exception.BusinessException;
import com.girisk.config.SportsRiskProperties;
import com.girisk.sports.dto.FixtureLimitOverrideRequest;
import com.girisk.sports.dto.FixtureLimitParamsView;
import com.girisk.sports.model.FixtureLimitOverride;
import com.girisk.sports.model.SportsMatch;
import com.girisk.sports.repository.SportsMatchRepository;
import com.girisk.sports.store.FixtureLimitOverrideStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class FixtureLimitParamsService {

    private static final Logger log = LoggerFactory.getLogger(FixtureLimitParamsService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SportsMatchRepository matchRepository;
    private final FixtureLimitOverrideStore overrideStore;
    private final ScopeLimitParamsService scopeLimitParamsService;
    private final SportsRiskProperties props;
    private final ScopeDutyAuth dutyAuth;
    private final ScopeRiskConfigDispatchService configDispatch;
    private final StringRedisTemplate redis;
    private final ObjectProvider<OperatorScopeService> operatorScope;
    private final ObjectProvider<OpsAuditService> opsAudit;

    @Autowired
    public FixtureLimitParamsService(
            SportsMatchRepository matchRepository,
            FixtureLimitOverrideStore overrideStore,
            ScopeLimitParamsService scopeLimitParamsService,
            SportsRiskProperties props,
            ScopeDutyAuth dutyAuth,
            ScopeRiskConfigDispatchService configDispatch,
            ObjectProvider<StringRedisTemplate> redis,
            ObjectProvider<OperatorScopeService> operatorScope,
            ObjectProvider<OpsAuditService> opsAudit) {
        this.matchRepository = matchRepository;
        this.overrideStore = overrideStore;
        this.scopeLimitParamsService = scopeLimitParamsService;
        this.props = props;
        this.dutyAuth = dutyAuth;
        this.configDispatch = configDispatch;
        this.redis = redis.getIfAvailable();
        this.operatorScope = operatorScope;
        this.opsAudit = opsAudit;
    }

    public FixtureLimitParamsService(
            SportsMatchRepository matchRepository,
            FixtureLimitOverrideStore overrideStore,
            ScopeLimitParamsService scopeLimitParamsService,
            SportsRiskProperties props,
            ScopeDutyAuth dutyAuth,
            ScopeRiskConfigDispatchService configDispatch,
            ObjectProvider<StringRedisTemplate> redis) {
        this(matchRepository, overrideStore, scopeLimitParamsService, props, dutyAuth, configDispatch,
                redis, emptyProvider(), emptyProvider());
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return null;
            }

            @Override
            public T getObject(Object... args) {
                return null;
            }

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getIfUnique() {
                return null;
            }
        };
    }

    private String resolveActor() {
        OperatorScopeService scope = operatorScope.getIfAvailable();
        if (scope != null) {
            return scope.requireActor();
        }
        return dutyAuth.currentUsername();
    }

    private void audit(String type, String title, String detail) {
        OpsAuditService a = opsAudit.getIfAvailable();
        if (a != null) {
            a.record(type, title, detail);
        }
    }

    /** 解析本场有效参数：总体 → 球类 → 联赛 → 赛事覆盖。 */
    public EffectiveParams resolve(String matchCode) {
        SportsMatch match = matchRepository.findByCode(matchCode)
                .orElseThrow(() -> new BusinessException("比赛不存在: " + matchCode));
        return resolve(match);
    }

    public EffectiveParams resolve(SportsMatch match) {
        ScopeLimitParamsService.Resolved r = scopeLimitParamsService.resolveForMatch(match);
        Optional<FixtureLimitOverride> ov = overrideStore.get(match.matchCode());
        return new EffectiveParams(
                match,
                ov.orElse(null),
                r.delta(),
                r.seedPayoutYuan(),
                r.maxWorstLossYuan(),
                r.maxBetPayoutYuan(),
                r.matchOverrideActive());
    }

    public FixtureLimitParamsView getView(String matchCode) {
        EffectiveParams p = resolve(matchCode);
        FixtureLimitOverride ov = p.override();
        return new FixtureLimitParamsView(
                matchCode,
                p.delta(),
                p.seedPayoutYuan(),
                p.maxWorstLossYuan(),
                p.maxBetPayoutYuan(),
                p.overrideActive(),
                p.match().delta(),
                p.match().exposureThreshold(),
                BigDecimal.valueOf(props.getSeedPayoutYuan()),
                BigDecimal.valueOf(props.getMaxWorstLossYuan()),
                props.getMaxBetPayoutYuan() > 0 ? BigDecimal.valueOf(props.getMaxBetPayoutYuan()) : null,
                ov != null ? ov.delta() : null,
                ov != null ? ov.seedPayoutYuan() : null,
                ov != null ? ov.maxWorstLossYuan() : null,
                ov != null ? ov.maxBetPayoutYuan() : null,
                ov != null ? ov.updatedBy() : null,
                ov != null ? ov.updatedAt() : null);
    }

    public FixtureLimitParamsView upsert(String matchCode, FixtureLimitOverrideRequest req) {
        dutyAuth.requireWrite(com.girisk.sports.model.LimitScopeType.MATCH);
        SportsMatch match = matchRepository.findByCode(matchCode)
                .orElseThrow(() -> new BusinessException("比赛不存在: " + matchCode));
        if (req == null) {
            throw new BusinessException("请求体不能为空");
        }
        validate(req);

        FixtureLimitOverride next = new FixtureLimitOverride(
                matchCode,
                req.delta(),
                req.seedPayoutYuan(),
                req.maxWorstLossYuan(),
                req.maxBetPayoutYuan(),
                resolveActor(),
                Instant.now());

        if (!next.hasAny()) {
            overrideStore.delete(matchCode);
            log.info("Cleared fixture limit override match={}", matchCode);
            audit(OpsAuditService.DUTY_FIXTURE_LIMIT, "清除赛事限额 " + matchCode, "by=" + next.updatedBy());
        } else {
            overrideStore.put(next);
            // 同步 δ / 敞口阈值到赛事库，看板与其它读库路径一致
            BigDecimal dbDelta = next.delta() != null ? next.delta() : match.delta();
            BigDecimal dbThreshold = next.maxWorstLossYuan() != null
                    ? next.maxWorstLossYuan() : match.exposureThreshold();
            matchRepository.updateMeta(
                    matchCode, match.homeTeam(), match.awayTeam(),
                    match.sportOrDefault(), match.leagueCodeOrDefault(), match.leagueNameOrDefault(),
                    dbThreshold, dbDelta);
            log.info("Saved fixture limit override match={} by={} delta={} seed={} maxWorst={} maxBet={}",
                    matchCode, next.updatedBy(), next.delta(), next.seedPayoutYuan(),
                    next.maxWorstLossYuan(), next.maxBetPayoutYuan());
            audit(OpsAuditService.DUTY_FIXTURE_LIMIT, "更新赛事限额 " + matchCode,
                    "delta=" + next.delta()
                            + " seed=" + next.seedPayoutYuan()
                            + " maxWorst=" + next.maxWorstLossYuan()
                            + " maxBet=" + next.maxBetPayoutYuan()
                            + " by=" + next.updatedBy());
        }

        configDispatch.afterScopeWrite(com.girisk.sports.model.LimitScopeType.MATCH, matchCode);
        EffectiveParams effective = resolve(matchCode);
        patchFixtureViewReplayStats(matchCode, effective);
        return getView(matchCode);
    }

    public FixtureLimitParamsView clear(String matchCode) {
        dutyAuth.requireWrite(com.girisk.sports.model.LimitScopeType.MATCH);
        matchRepository.findByCode(matchCode)
                .orElseThrow(() -> new BusinessException("比赛不存在: " + matchCode));
        String by = resolveActor();
        overrideStore.delete(matchCode);
        log.info("Cleared fixture limit override match={} by={}", matchCode, by);
        audit(OpsAuditService.DUTY_FIXTURE_LIMIT, "清除赛事限额 " + matchCode, "by=" + by);
        configDispatch.afterScopeWrite(com.girisk.sports.model.LimitScopeType.MATCH, matchCode);
        EffectiveParams effective = resolve(matchCode);
        patchFixtureViewReplayStats(matchCode, effective);
        return getView(matchCode);
    }

    private void validate(FixtureLimitOverrideRequest req) {
        if (req.delta() != null && (req.delta().compareTo(BigDecimal.ZERO) < 0
                || req.delta().compareTo(BigDecimal.ONE) > 0)) {
            throw new BusinessException("delta 须在 0～1 之间");
        }
        if (req.seedPayoutYuan() != null && req.seedPayoutYuan().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("seedPayoutYuan 不能为负");
        }
        if (req.maxWorstLossYuan() != null && req.maxWorstLossYuan().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("maxWorstLossYuan 不能为负");
        }
        if (req.maxBetPayoutYuan() != null && req.maxBetPayoutYuan().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("maxBetPayoutYuan 不能为负");
        }
    }

    /** 同步有效参数到 Redis 物化视图的 replayStats，赛事汇总条立刻可见。 */
    private void patchFixtureViewReplayStats(String matchCode, EffectiveParams p) {
        if (redis == null) {
            return;
        }
        String key = "girisk:view:fixture:" + matchCode;
        if (!Boolean.TRUE.equals(redis.hasKey(key))) {
            return;
        }
        try {
            Object raw = redis.opsForHash().get(key, "replayStats");
            Map<String, Object> stats = new LinkedHashMap<>();
            if (raw != null && !raw.toString().isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = MAPPER.readValue(raw.toString(), Map.class);
                if (parsed != null) {
                    stats.putAll(parsed);
                }
            }
            stats.put("delta", p.delta().doubleValue());
            stats.put("seedPayoutYuan", p.seedPayoutYuan().doubleValue());
            stats.put("maxWorstLossYuan", p.maxWorstLossYuan().doubleValue());
            if (p.maxBetPayoutYuan() != null) {
                stats.put("maxBetPayoutYuan", p.maxBetPayoutYuan().doubleValue());
            } else {
                stats.remove("maxBetPayoutYuan");
            }
            redis.opsForHash().put(key, "replayStats", MAPPER.writeValueAsString(stats));
            redis.opsForHash().put(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("Failed to patch fixture view replayStats match={}: {}", matchCode, e.getMessage());
        }
    }

    public record EffectiveParams(
            SportsMatch match,
            FixtureLimitOverride override,
            BigDecimal delta,
            BigDecimal seedPayoutYuan,
            BigDecimal maxWorstLossYuan,
            BigDecimal maxBetPayoutYuan,
            boolean overrideActive
    ) {
        public long maxWorstLossCents() {
            return Math.round(Math.abs(maxWorstLossYuan.doubleValue()) * 100);
        }
    }
}
