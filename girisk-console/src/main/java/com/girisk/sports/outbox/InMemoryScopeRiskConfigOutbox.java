package com.girisk.sports.outbox;

import com.girisk.config.RiskKafkaProperties;
import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeGateOverride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@ConditionalOnProperty(name = "girisk.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryScopeRiskConfigOutbox implements ScopeRiskConfigOutbox {

    private static final Logger log = LoggerFactory.getLogger(InMemoryScopeRiskConfigOutbox.class);

    private final ConcurrentLinkedQueue<ScopeRiskConfigOutboxEntry> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ScopeRiskConfigOutboxEntry> dlq = new ConcurrentLinkedQueue<>();
    private final RiskKafkaProperties kafkaProperties;

    public InMemoryScopeRiskConfigOutbox(RiskKafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    /** 单测无 Spring 时用。 */
    public InMemoryScopeRiskConfigOutbox() {
        this.kafkaProperties = new RiskKafkaProperties();
    }

    @Override
    public boolean isRelayEnabled() {
        return kafkaProperties.isEnabled() && kafkaProperties.isConfigOutboxEnabled();
    }

    @Override
    public void enqueue(LimitScopeType type, String scopeKey) {
        if (!isRelayEnabled() || type == null || scopeKey == null || scopeKey.isBlank()) {
            return;
        }
        String key = ScopeGateOverride.normalizeKey(type, scopeKey);
        queue.offer(new ScopeRiskConfigOutboxEntry(type, key, System.currentTimeMillis(), 0));
    }

    @Override
    public Optional<ScopeRiskConfigOutboxEntry> claim() {
        return Optional.ofNullable(queue.poll());
    }

    @Override
    public void requeue(ScopeRiskConfigOutboxEntry entry) {
        if (entry != null) {
            queue.offer(entry.withAttempt(entry.attempts() + 1));
        }
    }

    @Override
    public void deadLetter(ScopeRiskConfigOutboxEntry entry, String reason) {
        if (entry != null) {
            dlq.offer(entry);
            log.error(
                    "config outbox DLQ (memory) scope={}/{} attempts={} reason={}",
                    entry.scopeType(),
                    entry.scopeKey(),
                    entry.attempts(),
                    reason);
        }
    }

    @Override
    public long pendingDepth() {
        return queue.size();
    }

    List<ScopeRiskConfigOutboxEntry> drainDlqForTest() {
        List<ScopeRiskConfigOutboxEntry> out = new ArrayList<>();
        ScopeRiskConfigOutboxEntry e;
        while ((e = dlq.poll()) != null) {
            out.add(e);
        }
        return out;
    }
}
