package com.girisk.sports.service;

import com.girisk.common.exception.BusinessException;
import com.girisk.sports.dto.ScopeGateOverrideRequest;
import com.girisk.sports.dto.ScopeGateParamsView;
import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeGateOverride;
import com.girisk.sports.model.ScopeLimitOverride;
import com.girisk.sports.model.SportsMatch;
import com.girisk.sports.repository.SportsMatchRepository;
import com.girisk.sports.store.ScopeGateOverrideStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 三开关：总开关(trading) / 限额门控 / 敞口门控。
 * 继承：MATCH &gt; LEAGUE &gt; SPORT &gt; OVERALL(默认) &gt; 系统全开。
 */
@Service
public class ScopeGateService {

    private static final Logger log = LoggerFactory.getLogger(ScopeGateService.class);

    private final ScopeGateOverrideStore store;
    private final SportsMatchRepository matchRepository;
    private final ScopeDutyAuth dutyAuth;
    private final ScopeRiskConfigDispatchService configDispatch;

    public ScopeGateService(
            ScopeGateOverrideStore store,
            SportsMatchRepository matchRepository,
            ScopeDutyAuth dutyAuth,
            ScopeRiskConfigDispatchService configDispatch) {
        this.store = store;
        this.matchRepository = matchRepository;
        this.dutyAuth = dutyAuth;
        this.configDispatch = configDispatch;
    }

    public ScopeGateParamsView getView(LimitScopeType type, String scopeKey) {
        String key = ScopeGateOverride.normalizeKey(type, scopeKey);
        Resolved inherited = inheritedBefore(type, key);
        Optional<ScopeGateOverride> ov = store.get(type, key);
        Resolved effective = apply(inherited, ov.orElse(null), type.name());
        return toView(type, key, effective, inherited, ov.orElse(null));
    }

    public ScopeGateParamsView getMatchView(String matchCode) {
        SportsMatch match = matchRepository.findByCode(matchCode)
                .orElseThrow(() -> new BusinessException("比赛不存在: " + matchCode));
        return getView(LimitScopeType.MATCH, match.matchCode());
    }

    public ScopeGateParamsView upsert(LimitScopeType type, String scopeKey, ScopeGateOverrideRequest req) {
        dutyAuth.requireWrite(type);
        if (req == null) {
            throw new BusinessException("请求体不能为空");
        }
        String key = ScopeGateOverride.normalizeKey(type, scopeKey);
        if (type == LimitScopeType.SPORT && (key.isBlank() || "_".equals(key))) {
            throw new BusinessException("sport scopeKey required");
        }
        if (type == LimitScopeType.LEAGUE && !key.contains(":")) {
            throw new BusinessException("league scopeKey 应为 sport:leagueCode");
        }
        if (type == LimitScopeType.MATCH) {
            if (matchRepository.findByCode(key).isEmpty()) {
                throw new BusinessException("比赛不存在: " + key);
            }
        }
        String by = req.operatorId() != null && !req.operatorId().isBlank()
                ? req.operatorId() : dutyAuth.currentUsername();
        ScopeGateOverride next = new ScopeGateOverride(
                type, key,
                req.tradingEnabled(),
                req.limitGateEnabled(),
                req.exposureGateEnabled(),
                by,
                Instant.now());
        if (!next.hasAny()) {
            store.delete(type, key);
            log.info("Cleared scope gates {}/{} by={}", type, key, by);
        } else {
            store.put(next);
            log.info("Saved scope gates {}/{} trading={} limit={} exposure={} by={}",
                    type, key, next.tradingEnabled(), next.limitGateEnabled(),
                    next.exposureGateEnabled(), by);
        }
        syncTradingStatus(type, key, next.tradingEnabled());
        configDispatch.afterScopeWrite(type, key);
        return getView(type, key);
    }

    public ScopeGateParamsView clear(LimitScopeType type, String scopeKey) {
        dutyAuth.requireWrite(type);
        String key = ScopeGateOverride.normalizeKey(type, scopeKey);
        store.delete(type, key);
        log.info("Cleared scope gates {}/{}", type, key);
        configDispatch.afterScopeWrite(type, key);
        return getView(type, key);
    }

    /** 供状态 API 回写本层 trading 覆盖（不鉴权二次，由调用方已鉴权）。 */
    public void mirrorTrading(LimitScopeType type, String scopeKey, boolean tradingEnabled, String updatedBy) {
        String key = ScopeGateOverride.normalizeKey(type, scopeKey);
        Optional<ScopeGateOverride> cur = store.get(type, key);
        ScopeGateOverride next = new ScopeGateOverride(
                type,
                key,
                tradingEnabled,
                cur.map(ScopeGateOverride::limitGateEnabled).orElse(null),
                cur.map(ScopeGateOverride::exposureGateEnabled).orElse(null),
                updatedBy != null ? updatedBy : dutyAuth.currentUsername(),
                Instant.now());
        store.put(next);
        configDispatch.afterScopeWrite(type, key);
    }

    /**
     * 解析某场有效门控。match.status=SUSPENDED 视为本场总开关关闭（兼容旧停盘）。
     */
    public EffectiveGates resolveForMatch(SportsMatch match) {
        Resolved r = defaults();
        r = apply(r, store.get(LimitScopeType.OVERALL, "_").orElse(null), "OVERALL");
        r = apply(r, store.get(LimitScopeType.SPORT, match.sportOrDefault()).orElse(null), "SPORT");
        r = apply(r, store.get(
                LimitScopeType.LEAGUE,
                ScopeLimitOverride.leagueKey(match.sportOrDefault(), match.leagueCodeOrDefault())
        ).orElse(null), "LEAGUE");
        r = apply(r, store.get(LimitScopeType.MATCH, match.matchCode()).orElse(null), "MATCH");
        boolean trading = r.trading;
        String tradingSource = r.tradingSource;
        if (!"ACTIVE".equals(match.status())) {
            trading = false;
            tradingSource = "MATCH_STATUS";
        }
        return new EffectiveGates(
                trading, r.limitGate, r.exposureGate,
                tradingSource, r.limitSource, r.exposureSource);
    }

    private void syncTradingStatus(LimitScopeType type, String key, Boolean tradingEnabled) {
        if (tradingEnabled == null) {
            return;
        }
        String status = tradingEnabled ? "ACTIVE" : "SUSPENDED";
        switch (type) {
            case OVERALL -> matchRepository.updateStatusAll(status);
            case SPORT -> matchRepository.updateStatusBySport(key, status);
            case LEAGUE -> {
                String[] parts = ScopeLimitOverride.splitLeagueKey(key);
                matchRepository.updateStatusByLeague(parts[0], parts[1], status);
            }
            case MATCH -> matchRepository.updateStatus(key, status);
        }
    }

    private Resolved inheritedBefore(LimitScopeType type, String key) {
        Resolved r = defaults();
        if (type == LimitScopeType.OVERALL) {
            return r;
        }
        r = apply(r, store.get(LimitScopeType.OVERALL, "_").orElse(null), "OVERALL");
        if (type == LimitScopeType.SPORT) {
            return r;
        }
        String sport = key.contains(":") ? ScopeLimitOverride.splitLeagueKey(key)[0] : key;
        if (type == LimitScopeType.LEAGUE) {
            r = apply(r, store.get(LimitScopeType.SPORT, sport).orElse(null), "SPORT");
            return r;
        }
        // MATCH — key is matchCode; need sport/league from DB
        SportsMatch match = matchRepository.findByCode(key).orElse(null);
        if (match == null) {
            return r;
        }
        r = apply(r, store.get(LimitScopeType.SPORT, match.sportOrDefault()).orElse(null), "SPORT");
        r = apply(r, store.get(
                LimitScopeType.LEAGUE,
                ScopeLimitOverride.leagueKey(match.sportOrDefault(), match.leagueCodeOrDefault())
        ).orElse(null), "LEAGUE");
        return r;
    }

    private static Resolved defaults() {
        return new Resolved(true, true, true, "DEFAULT", "DEFAULT", "DEFAULT");
    }

    private static Resolved apply(Resolved base, ScopeGateOverride ov, String source) {
        if (ov == null || !ov.hasAny()) {
            return base;
        }
        return new Resolved(
                ov.tradingEnabled() != null ? ov.tradingEnabled() : base.trading,
                ov.limitGateEnabled() != null ? ov.limitGateEnabled() : base.limitGate,
                ov.exposureGateEnabled() != null ? ov.exposureGateEnabled() : base.exposureGate,
                ov.tradingEnabled() != null ? source : base.tradingSource,
                ov.limitGateEnabled() != null ? source : base.limitSource,
                ov.exposureGateEnabled() != null ? source : base.exposureSource);
    }

    private ScopeGateParamsView toView(
            LimitScopeType type,
            String key,
            Resolved effective,
            Resolved inherited,
            ScopeGateOverride ov) {
        boolean active = ov != null && ov.hasAny();
        return new ScopeGateParamsView(
                type.name(),
                key,
                effective.trading,
                effective.limitGate,
                effective.exposureGate,
                effective.tradingSource,
                effective.limitSource,
                effective.exposureSource,
                active,
                ov != null ? ov.tradingEnabled() : null,
                ov != null ? ov.limitGateEnabled() : null,
                ov != null ? ov.exposureGateEnabled() : null,
                inherited.trading,
                inherited.limitGate,
                inherited.exposureGate,
                dutyAuth.canWrite(type),
                ov != null ? ov.updatedBy() : null,
                ov != null && ov.updatedAt() != null ? ov.updatedAt().toString() : null);
    }

    public record EffectiveGates(
            boolean tradingEnabled,
            boolean limitGateEnabled,
            boolean exposureGateEnabled,
            String tradingSource,
            String limitGateSource,
            String exposureGateSource
    ) {
    }

    private record Resolved(
            boolean trading,
            boolean limitGate,
            boolean exposureGate,
            String tradingSource,
            String limitSource,
            String exposureSource
    ) {
    }
}
