package com.girisk.sports.service;

import com.girisk.flink.ScopeRiskConfigKafkaPublisher;
import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeGateOverride;
import com.girisk.sports.model.ScopeLimitOverride;
import com.girisk.sports.outbox.ScopeRiskConfigOutbox;
import com.girisk.sports.store.FixtureLimitOverrideStore;
import com.girisk.sports.store.ScopeGateOverrideStore;
import com.girisk.sports.store.ScopeLimitOverrideStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 值班变更后同步到 {@code girisk.config.v1}。
 * <ul>
 *   <li>outbox 开启：store 已在 Redis MULTI 中入队，本服务 {@link #afterScopeWrite} no-op，由 Poller 投递</li>
 *   <li>outbox 关闭：同步 publish（兼容）</li>
 *   <li>运维全量同步：始终 {@link #publishScopeSnapshot}</li>
 * </ul>
 */
@Service
public class ScopeRiskConfigDispatchService {

    private final ScopeGateOverrideStore gateStore;
    private final ScopeLimitOverrideStore limitStore;
    private final FixtureLimitOverrideStore fixtureLimitStore;
    private final ScopeRiskConfigKafkaPublisher publisher;
    private final ScopeRiskConfigOutbox outbox;

    public ScopeRiskConfigDispatchService(
            ScopeGateOverrideStore gateStore,
            ScopeLimitOverrideStore limitStore,
            FixtureLimitOverrideStore fixtureLimitStore,
            ObjectProvider<ScopeRiskConfigKafkaPublisher> publisher,
            ObjectProvider<ScopeRiskConfigOutbox> outbox) {
        this.gateStore = gateStore;
        this.limitStore = limitStore;
        this.fixtureLimitStore = fixtureLimitStore;
        this.publisher = publisher.getIfAvailable();
        this.outbox = outbox.getIfAvailable();
    }

    /** 写路径回调：outbox 已入队则跳过；否则同步发 Kafka。 */
    public void afterScopeWrite(LimitScopeType type, String scopeKey) {
        if (outbox != null && outbox.isRelayEnabled()) {
            return;
        }
        publishScopeSnapshot(type, scopeKey);
    }

    public void publishScopeSnapshot(LimitScopeType type, String scopeKey) {
        if (publisher == null) {
            return;
        }
        String key = ScopeGateOverride.normalizeKey(type, scopeKey);
        ScopeGateOverride gates = gateStore.get(type, key).orElse(null);
        ScopeLimitOverride limits = resolveLimits(type, key);
        boolean empty = (gates == null || !gates.hasAny()) && (limits == null || !limits.hasAny());
        publisher.publishSnapshot(type, key, gates, limits, empty);
    }

    private ScopeLimitOverride resolveLimits(LimitScopeType type, String key) {
        if (type == LimitScopeType.MATCH) {
            var fix = fixtureLimitStore.get(key).orElse(null);
            if (fix == null || !fix.hasAny()) {
                return null;
            }
            return new ScopeLimitOverride(
                    LimitScopeType.MATCH,
                    key,
                    fix.delta(),
                    fix.seedPayoutYuan(),
                    fix.maxWorstLossYuan(),
                    fix.maxBetPayoutYuan(),
                    fix.updatedBy(),
                    fix.updatedAt());
        }
        return limitStore.get(type, key).orElse(null);
    }
}
