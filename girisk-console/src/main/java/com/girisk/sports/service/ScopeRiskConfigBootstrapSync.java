package com.girisk.sports.service;

import com.girisk.event.repository.RiskEventRepository;
import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeGateOverride;
import com.girisk.sports.model.ScopeLimitOverride;
import com.girisk.sports.store.FixtureLimitOverrideStore;
import com.girisk.sports.store.ScopeGateOverrideStore;
import com.girisk.sports.store.ScopeLimitOverrideStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 启动 / 运维：把 Redis（或内存）里全部值班覆盖重刷到 {@code girisk.config.v1}，修复漂移。
 */
@Service
@ConditionalOnProperty(name = "girisk.kafka.enabled", havingValue = "true")
public class ScopeRiskConfigBootstrapSync {

    private static final Logger log = LoggerFactory.getLogger(ScopeRiskConfigBootstrapSync.class);

    private final ScopeGateOverrideStore gateStore;
    private final ScopeLimitOverrideStore limitStore;
    private final FixtureLimitOverrideStore fixtureLimitStore;
    private final ScopeRiskConfigDispatchService dispatch;
    private final RiskEventRepository eventRepository;

    public ScopeRiskConfigBootstrapSync(
            ScopeGateOverrideStore gateStore,
            ScopeLimitOverrideStore limitStore,
            FixtureLimitOverrideStore fixtureLimitStore,
            ScopeRiskConfigDispatchService dispatch,
            ObjectProvider<RiskEventRepository> eventRepository) {
        this.gateStore = gateStore;
        this.limitStore = limitStore;
        this.fixtureLimitStore = fixtureLimitStore;
        this.dispatch = dispatch;
        this.eventRepository = eventRepository.getIfAvailable();
    }

    public void syncAllQuietly() {
        try {
            Map<String, Object> r = syncAll();
            log.info("config.v1 bootstrap sync done: {}", r);
        } catch (Exception e) {
            log.error("config.v1 bootstrap sync failed: {}", e.getMessage(), e);
            if (eventRepository != null) {
                try {
                    eventRepository.insert(
                            "CONFIG_BOOTSTRAP_SYNC_FAIL",
                            "ERROR",
                            null,
                            "system",
                            "启动全量同步 config.v1 失败",
                            e.getMessage());
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    /** @return published / failed / scopes */
    public Map<String, Object> syncAll() {
        Set<String> scopeIds = new LinkedHashSet<>();
        for (ScopeGateOverride g : gateStore.listAll()) {
            scopeIds.add(g.scopeType().name() + "|" + g.scopeKey());
        }
        for (ScopeLimitOverride l : limitStore.listAll()) {
            scopeIds.add(l.scopeType().name() + "|" + l.scopeKey());
        }
        for (var f : fixtureLimitStore.listAll()) {
            scopeIds.add(LimitScopeType.MATCH.name() + "|" + f.matchCode());
        }

        int published = 0;
        int failed = 0;
        for (String id : scopeIds) {
            int cut = id.indexOf('|');
            LimitScopeType type = LimitScopeType.valueOf(id.substring(0, cut));
            String key = id.substring(cut + 1);
            try {
                dispatch.publishScopeSnapshot(type, key);
                published++;
            } catch (Exception e) {
                failed++;
                log.warn("bootstrap sync scope {}/{} failed: {}", type, key, e.getMessage());
            }
        }
        if (eventRepository != null) {
            eventRepository.insert(
                    "CONFIG_BOOTSTRAP_SYNC",
                    failed == 0 ? "INFO" : "WARN",
                    null,
                    "system",
                    "config.v1 全量同步",
                    "scopes=" + scopeIds.size() + " published=" + published + " failed=" + failed);
        }
        return Map.of(
                "scopes", scopeIds.size(),
                "published", published,
                "failed", failed);
    }
}
