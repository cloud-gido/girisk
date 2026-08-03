package com.girisk.sports.outbox;

import com.girisk.sports.model.LimitScopeType;

/**
 * Redis / 内存 outbox 中的一条配置同步任务。
 * Poller 领取后从 SoT 重读最新快照再发 Kafka（latest-wins）。
 */
public record ScopeRiskConfigOutboxEntry(
        LimitScopeType scopeType,
        String scopeKey,
        long enqueuedAtEpochMs,
        int attempts) {

    public ScopeRiskConfigOutboxEntry withAttempt(int nextAttempts) {
        return new ScopeRiskConfigOutboxEntry(scopeType, scopeKey, enqueuedAtEpochMs, nextAttempts);
    }

    public String dedupeKey() {
        return scopeType.name() + ":" + scopeKey;
    }
}
