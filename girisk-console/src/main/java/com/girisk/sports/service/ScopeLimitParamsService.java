package com.girisk.sports.service;

import com.girisk.audit.OpsAuditService;
import com.girisk.auth.OperatorScopeService;
import com.girisk.common.exception.BusinessException;
import com.girisk.config.SportsRiskProperties;
import com.girisk.sports.dto.FixtureLimitOverrideRequest;
import com.girisk.sports.dto.ScopeLimitParamsView;
import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeLimitOverride;
import com.girisk.sports.model.SportsMatch;
import com.girisk.sports.store.FixtureLimitOverrideStore;
import com.girisk.sports.store.ScopeLimitOverrideStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * 四级限额：总体 → 球类 → 联赛 → 赛事。
 * 赛事覆盖仍写入 {@code girisk:override:fixture:*} 以兼容既有 API。
 */
@Service
public class ScopeLimitParamsService {

    private static final Logger log = LoggerFactory.getLogger(ScopeLimitParamsService.class);

    private final ScopeLimitOverrideStore scopeStore;
    private final FixtureLimitOverrideStore fixtureStore;
    private final SportsRiskProperties props;
    private final ScopeDutyAuth dutyAuth;
    private final ScopeRiskConfigDispatchService configDispatch;
    private final ObjectProvider<OperatorScopeService> operatorScope;
    private final ObjectProvider<OpsAuditService> opsAudit;

    @Autowired
    public ScopeLimitParamsService(
            ScopeLimitOverrideStore scopeStore,
            FixtureLimitOverrideStore fixtureStore,
            SportsRiskProperties props,
            ScopeDutyAuth dutyAuth,
            ScopeRiskConfigDispatchService configDispatch,
            ObjectProvider<OperatorScopeService> operatorScope,
            ObjectProvider<OpsAuditService> opsAudit) {
        this.scopeStore = scopeStore;
        this.fixtureStore = fixtureStore;
        this.props = props;
        this.dutyAuth = dutyAuth;
        this.configDispatch = configDispatch;
        this.operatorScope = operatorScope;
        this.opsAudit = opsAudit;
    }

    public ScopeLimitParamsService(
            ScopeLimitOverrideStore scopeStore,
            FixtureLimitOverrideStore fixtureStore,
            SportsRiskProperties props,
            ScopeDutyAuth dutyAuth,
            ScopeRiskConfigDispatchService configDispatch) {
        this(scopeStore, fixtureStore, props, dutyAuth, configDispatch, emptyProvider(), emptyProvider());
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

    public ScopeLimitParamsView getView(LimitScopeType type, String scopeKey) {
        String key = normalizeKey(type, scopeKey);
        if (type == LimitScopeType.MATCH) {
            throw new BusinessException("赛事请使用 /matches/{code}/limit-override");
        }
        Layer inherited = inheritedBefore(type, key);
        Optional<ScopeLimitOverride> ov = scopeStore.get(type, key);
        Layer effective = apply(inherited, ov.orElse(null));
        return toView(type, key, effective, inherited, ov.orElse(null));
    }

    public ScopeLimitParamsView upsert(LimitScopeType type, String scopeKey, FixtureLimitOverrideRequest req) {
        if (type == LimitScopeType.MATCH) {
            throw new BusinessException("赛事请使用 /matches/{code}/limit-override");
        }
        dutyAuth.requireWrite(type);
        if (req == null) {
            throw new BusinessException("请求体不能为空");
        }
        validate(req);
        String key = normalizeKey(type, scopeKey);
        String by = resolveActor();
        ScopeLimitOverride next = new ScopeLimitOverride(
                type,
                key,
                req.delta(),
                req.seedPayoutYuan(),
                req.maxWorstLossYuan(),
                req.maxBetPayoutYuan(),
                by,
                Instant.now());
        if (!next.hasAny()) {
            scopeStore.delete(type, key);
            log.info("Cleared scope limit override {}/{}", type, key);
            audit(OpsAuditService.DUTY_LIMIT_CLEAR, "清除限额 " + type + "/" + key, "by=" + by);
        } else {
            scopeStore.put(next);
            log.info("Saved scope limit override {}/{} by={}", type, key, next.updatedBy());
            audit(OpsAuditService.DUTY_LIMIT_UPSERT, "更新限额 " + type + "/" + key,
                    "delta=" + next.delta()
                            + " seed=" + next.seedPayoutYuan()
                            + " maxWorst=" + next.maxWorstLossYuan()
                            + " maxBet=" + next.maxBetPayoutYuan()
                            + " by=" + by);
        }
        configDispatch.afterScopeWrite(type, key);
        return getView(type, key);
    }

    public ScopeLimitParamsView clear(LimitScopeType type, String scopeKey) {
        if (type == LimitScopeType.MATCH) {
            throw new BusinessException("赛事请使用 /matches/{code}/limit-override");
        }
        dutyAuth.requireWrite(type);
        String key = normalizeKey(type, scopeKey);
        String by = resolveActor();
        scopeStore.delete(type, key);
        audit(OpsAuditService.DUTY_LIMIT_CLEAR, "清除限额 " + type + "/" + key, "by=" + by);
        configDispatch.afterScopeWrite(type, key);
        return getView(type, key);
    }

    /**
     * 解析某场有效参数：全局默认 ← 总体 ← 球类 ← 联赛 ← 赛事覆盖。
     * 无任何覆盖时，赛事库 δ / 敞口阈值仍可作为落库默认。
     */
    public Resolved resolveForMatch(SportsMatch match) {
        Layer layer = globalLayer();
        layer = apply(layer, scopeStore.get(LimitScopeType.OVERALL, "_").orElse(null));
        layer = apply(layer, scopeStore.get(LimitScopeType.SPORT, match.sportOrDefault()).orElse(null));
        layer = apply(layer, scopeStore.get(
                LimitScopeType.LEAGUE,
                ScopeLimitOverride.leagueKey(match.sportOrDefault(), match.leagueCodeOrDefault())).orElse(null));

        boolean anyParentOverride =
                scopeStore.get(LimitScopeType.OVERALL, "_").map(ScopeLimitOverride::hasAny).orElse(false)
                        || scopeStore.get(LimitScopeType.SPORT, match.sportOrDefault()).map(ScopeLimitOverride::hasAny).orElse(false)
                        || scopeStore.get(
                                LimitScopeType.LEAGUE,
                                ScopeLimitOverride.leagueKey(match.sportOrDefault(), match.leagueCodeOrDefault()))
                        .map(ScopeLimitOverride::hasAny).orElse(false);

        if (!anyParentOverride) {
            if (match.delta() != null) {
                layer = new Layer(match.delta(), layer.seed, layer.maxWorst, layer.maxBet);
            }
            if (match.exposureThreshold() != null) {
                layer = new Layer(layer.delta, layer.seed, match.exposureThreshold(), layer.maxBet);
            }
        }

        ScopeLimitOverride matchOv = fixtureStore.get(match.matchCode())
                .map(f -> new ScopeLimitOverride(
                        LimitScopeType.MATCH,
                        f.matchCode(),
                        f.delta(),
                        f.seedPayoutYuan(),
                        f.maxWorstLossYuan(),
                        f.maxBetPayoutYuan(),
                        f.updatedBy(),
                        f.updatedAt()))
                .orElse(null);
        Layer effective = apply(layer, matchOv);
        boolean active = matchOv != null && matchOv.hasAny();
        return new Resolved(effective.delta, effective.seed, effective.maxWorst, effective.maxBet, active, matchOv);
    }

    private Layer inheritedBefore(LimitScopeType type, String key) {
        Layer layer = globalLayer();
        if (type == LimitScopeType.OVERALL) {
            return layer;
        }
        layer = apply(layer, scopeStore.get(LimitScopeType.OVERALL, "_").orElse(null));
        if (type == LimitScopeType.SPORT) {
            return layer;
        }
        String sport = key.contains(":") ? key.substring(0, key.indexOf(':')) : key;
        layer = apply(layer, scopeStore.get(LimitScopeType.SPORT, sport).orElse(null));
        return layer;
    }

    private Layer globalLayer() {
        BigDecimal maxBet = props.getMaxBetPayoutYuan() > 0
                ? BigDecimal.valueOf(props.getMaxBetPayoutYuan()) : null;
        return new Layer(
                BigDecimal.valueOf(props.getDefaultDelta()),
                BigDecimal.valueOf(props.getSeedPayoutYuan()),
                BigDecimal.valueOf(props.getMaxWorstLossYuan()),
                maxBet);
    }

    private static Layer apply(Layer base, ScopeLimitOverride ov) {
        if (ov == null || !ov.hasAny()) {
            return base;
        }
        BigDecimal maxBet = ov.maxBetPayoutYuan() != null
                ? (ov.maxBetPayoutYuan().compareTo(BigDecimal.ZERO) <= 0 ? null : ov.maxBetPayoutYuan())
                : base.maxBet;
        return new Layer(
                ov.delta() != null ? ov.delta() : base.delta,
                ov.seedPayoutYuan() != null ? ov.seedPayoutYuan() : base.seed,
                ov.maxWorstLossYuan() != null ? ov.maxWorstLossYuan() : base.maxWorst,
                maxBet);
    }

    private static String normalizeKey(LimitScopeType type, String scopeKey) {
        if (type == LimitScopeType.OVERALL) {
            return "_";
        }
        if (scopeKey == null || scopeKey.isBlank()) {
            throw new BusinessException("scopeKey required for " + type);
        }
        return scopeKey.trim();
    }

    private static void validate(FixtureLimitOverrideRequest req) {
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

    private static ScopeLimitParamsView toView(
            LimitScopeType type,
            String key,
            Layer effective,
            Layer inherited,
            ScopeLimitOverride ov) {
        return new ScopeLimitParamsView(
                type.name(),
                key,
                effective.delta,
                effective.seed,
                effective.maxWorst,
                effective.maxBet,
                ov != null && ov.hasAny(),
                inherited.delta,
                inherited.seed,
                inherited.maxWorst,
                inherited.maxBet,
                ov != null ? ov.delta() : null,
                ov != null ? ov.seedPayoutYuan() : null,
                ov != null ? ov.maxWorstLossYuan() : null,
                ov != null ? ov.maxBetPayoutYuan() : null,
                ov != null ? ov.updatedBy() : null,
                ov != null ? ov.updatedAt() : null);
    }

    private record Layer(BigDecimal delta, BigDecimal seed, BigDecimal maxWorst, BigDecimal maxBet) {}

    public record Resolved(
            BigDecimal delta,
            BigDecimal seedPayoutYuan,
            BigDecimal maxWorstLossYuan,
            BigDecimal maxBetPayoutYuan,
            boolean matchOverrideActive,
            ScopeLimitOverride matchOverride
    ) {}
}
