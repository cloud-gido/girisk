package com.girisk.sports.outbox;

import com.girisk.sports.model.LimitScopeType;
import org.springframework.data.redis.core.RedisOperations;

import java.util.Optional;

/**
 * 值班配置 → {@code girisk.config.v1} 的 transactional outbox。
 * Redis 实现与覆盖写在同一 MULTI/EXEC；Poller 异步可靠投递。
 */
public interface ScopeRiskConfigOutbox {

    /** 独立入队（非事务路径，如运维补推）。 */
    void enqueue(LimitScopeType type, String scopeKey);

    /**
     * 在已有 {@code MULTI} 中追加 RPUSH（由 Redis store 调用）。
     * 非 Redis 实现可忽略 {@code ops}，退化为 {@link #enqueue}。
     */
    default void enqueueInTransaction(RedisOperations<String, String> ops, LimitScopeType type, String scopeKey) {
        enqueue(type, scopeKey);
    }

    /** 领取一条待发送任务（LPOP）；空则 empty。 */
    Optional<ScopeRiskConfigOutboxEntry> claim();

    /** 发送失败后重新入队（attempts+1）。 */
    void requeue(ScopeRiskConfigOutboxEntry entry);

    /** 超过最大重试后进入 DLQ。 */
    void deadLetter(ScopeRiskConfigOutboxEntry entry, String reason);

    long pendingDepth();

    /** Kafka 未启用时 store 可跳过入队。 */
    boolean isRelayEnabled();
}
