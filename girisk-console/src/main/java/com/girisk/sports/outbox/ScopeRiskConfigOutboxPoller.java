package com.girisk.sports.outbox;

import com.girisk.config.RiskKafkaProperties;
import com.girisk.event.repository.RiskEventRepository;
import com.girisk.sports.service.ScopeRiskConfigDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 消费 scope-config outbox → 从 SoT 组装快照 → {@code girisk.config.v1}。
 */
@Component
@ConditionalOnProperty(name = "girisk.kafka.enabled", havingValue = "true")
public class ScopeRiskConfigOutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(ScopeRiskConfigOutboxPoller.class);

    private final ScopeRiskConfigOutbox outbox;
    private final ScopeRiskConfigDispatchService dispatch;
    private final RiskKafkaProperties properties;
    private final RiskEventRepository eventRepository;

    public ScopeRiskConfigOutboxPoller(
            ScopeRiskConfigOutbox outbox,
            ScopeRiskConfigDispatchService dispatch,
            RiskKafkaProperties properties,
            ObjectProvider<RiskEventRepository> eventRepository) {
        this.outbox = outbox;
        this.dispatch = dispatch;
        this.properties = properties;
        this.eventRepository = eventRepository.getIfAvailable();
    }

    @Scheduled(fixedDelayString = "${girisk.kafka.config-outbox-poll-ms:500}")
    public void poll() {
        if (!properties.isConfigOutboxEnabled() || !outbox.isRelayEnabled()) {
            return;
        }
        try {
            drainBatch();
        } catch (Exception e) {
            // Redis 超时等：打日志后下一轮再试，勿打爆调度线程
            log.warn("config outbox poll skipped: {}", e.getMessage());
        }
    }

    private void drainBatch() {
        int batch = Math.max(1, properties.getConfigOutboxBatchSize());
        int maxAttempts = Math.max(1, properties.getConfigOutboxMaxAttempts());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < batch; i++) {
            Optional<ScopeRiskConfigOutboxEntry> claimed = outbox.claim();
            if (claimed.isEmpty()) {
                break;
            }
            ScopeRiskConfigOutboxEntry entry = claimed.get();
            if (!seen.add(entry.dedupeKey())) {
                continue;
            }
            if (entry.attempts() >= maxAttempts) {
                outbox.deadLetter(entry, "max_attempts=" + maxAttempts);
                auditDlq(entry);
                continue;
            }
            try {
                dispatch.publishScopeSnapshot(entry.scopeType(), entry.scopeKey());
            } catch (Exception e) {
                log.warn(
                        "config outbox publish failed scope={}/{} attempts={}: {}",
                        entry.scopeType(),
                        entry.scopeKey(),
                        entry.attempts(),
                        e.getMessage());
                if (entry.attempts() + 1 >= maxAttempts) {
                    outbox.deadLetter(entry.withAttempt(entry.attempts() + 1), e.getMessage());
                    auditDlq(entry);
                } else {
                    outbox.requeue(entry);
                }
            }
        }
    }

    private void auditDlq(ScopeRiskConfigOutboxEntry entry) {
        if (eventRepository == null) {
            return;
        }
        try {
            eventRepository.insert(
                    "CONFIG_OUTBOX_DLQ",
                    "ERROR",
                    null,
                    "system",
                    "config.v1 outbox 进入 DLQ",
                    "scope=" + entry.scopeType() + "/" + entry.scopeKey()
                            + " attempts=" + entry.attempts());
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
