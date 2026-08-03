package com.girisk.configcenter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.common.exception.BusinessException;
import com.girisk.configcenter.model.RiskConfigRelease;
import com.girisk.configcenter.repository.RiskConfigReleaseRepository;
import com.girisk.event.repository.RiskEventRepository;
import com.girisk.flink.RiskConfigKafkaPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ConfigReleaseService {

    private final RiskConfigReleaseRepository repository;
    private final RiskEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final RiskConfigKafkaPublisher configKafkaPublisher;

    public ConfigReleaseService(
            RiskConfigReleaseRepository repository,
            RiskEventRepository eventRepository,
            ObjectMapper objectMapper,
            ObjectProvider<RiskConfigKafkaPublisher> configKafkaPublisher) {
        this.repository = repository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.configKafkaPublisher = configKafkaPublisher.getIfAvailable();
    }

    public List<RiskConfigRelease> list() {
        return repository.findAll();
    }

    public RiskConfigRelease get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("配置版本不存在"));
    }

    public RiskConfigRelease currentPublished() {
        return repository.findPublished().orElse(null);
    }

    public RiskConfigRelease createDraft(Map<String, Object> body, String actor) {
        long epoch = repository.nextEpoch();
        String paramSetVersion = str(body, "paramSetVersion", "ps-v" + epoch);
        String ruleSetVersion = str(body, "ruleSetVersion", "rs-v" + epoch);
        String paramSetJson = toJson(body.getOrDefault("paramSet", defaultParamSet()));
        String ruleSetJson = toJson(body.getOrDefault("ruleSet", Map.of("version", ruleSetVersion, "rules", List.of())));
        RiskConfigRelease draft = new RiskConfigRelease(
                null, epoch, str(body, "scope", "global"), "DRAFT",
                paramSetVersion, ruleSetVersion, paramSetJson, ruleSetJson,
                str(body, "changeSummary", ""), actor,
                null, null, null, null, null,
                LocalDateTime.now(), null, null, null);
        long id = repository.insert(draft);
        return repository.findById(id).orElseThrow();
    }

    public RiskConfigRelease submit(long id, String actor) {
        RiskConfigRelease r = get(id);
        if (!"DRAFT".equals(r.status()) && !"REJECTED".equals(r.status())) {
            throw new BusinessException("仅草稿或已驳回版本可提交审批");
        }
        repository.markSubmitted(id, actor);
        eventRepository.insert("CONFIG_SUBMIT", "INFO", null, actor,
                "配置提交审批", "epoch=" + r.configEpoch());
        return get(id);
    }

    public RiskConfigRelease approve(long id, String actor, String ticket) {
        RiskConfigRelease r = get(id);
        if (!"PENDING_APPROVAL".equals(r.status())) {
            throw new BusinessException("仅待审批版本可通过");
        }
        if (actor != null && actor.equals(r.submittedBy())) {
            throw new BusinessException("审批人不能与提交人为同一人（双人复核）");
        }
        if (ticket == null || ticket.isBlank()) {
            throw new BusinessException("必须填写审批单号 approvalTicket");
        }
        repository.markApproved(id, actor, ticket);
        eventRepository.insert("CONFIG_APPROVE", "INFO", null, actor,
                "配置审批通过", "epoch=" + r.configEpoch() + " ticket=" + ticket);
        return get(id);
    }

    public RiskConfigRelease reject(long id, String actor, String reason) {
        RiskConfigRelease r = get(id);
        if (!"PENDING_APPROVAL".equals(r.status())) {
            throw new BusinessException("仅待审批版本可驳回");
        }
        repository.markRejected(id, actor, reason != null ? reason : "");
        eventRepository.insert("CONFIG_REJECT", "WARN", null, actor,
                "配置审批驳回", "epoch=" + r.configEpoch());
        return get(id);
    }

    public RiskConfigRelease publish(long id, String actor) {
        RiskConfigRelease r = get(id);
        if (!"APPROVED".equals(r.status())) {
            throw new BusinessException("仅已审批版本可发布");
        }
        repository.markPublished(id, actor);
        RiskConfigRelease published = get(id);
        if (configKafkaPublisher != null) {
            configKafkaPublisher.publish(published);
        }
        eventRepository.insert("CONFIG_PUBLISH", "INFO", null, actor,
                "配置已发布", "epoch=" + published.configEpoch() + " → girisk.config.v1");
        return published;
    }

    private Map<String, Object> defaultParamSet() {
        return Map.of(
                "version", "ps-default",
                "limit", Map.of("delta", 0.2, "basis", "payout", "initialSeedPayoutCents", 200000, "rejectBoundary", "GTE"),
                "exposure", Map.of("maxWorstLossCents", 100000, "grid", Map.of("home", 6, "away", 6, "liveScoreDynamic", true)),
                "decision", Map.of("limitDecisionEnabled", true, "unknownPlayTypePolicy", "REVIEW", "pendingReserveTtlMs", 30000)
        );
    }

    private String str(Map<String, Object> body, String key, String def) {
        Object v = body.get(key);
        return v == null || v.toString().isBlank() ? def : v.toString();
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
