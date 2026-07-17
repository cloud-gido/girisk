package com.girisk.decision.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.case_.model.RiskCase;
import com.girisk.case_.repository.RiskCaseRepository;
import com.girisk.common.exception.BusinessException;
import com.girisk.configcenter.model.RiskConfigRelease;
import com.girisk.configcenter.repository.RiskConfigReleaseRepository;
import com.girisk.decision.model.RiskDecisionLog;
import com.girisk.decision.repository.RiskDecisionLogRepository;
import com.girisk.event.model.RiskEvent;
import com.girisk.event.repository.RiskEventRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DecisionReplayService {

    private final RiskDecisionLogRepository decisionLogRepository;
    private final RiskCaseRepository caseRepository;
    private final RiskEventRepository eventRepository;
    private final RiskConfigReleaseRepository configReleaseRepository;
    private final ObjectMapper objectMapper;

    public DecisionReplayService(
            RiskDecisionLogRepository decisionLogRepository,
            RiskCaseRepository caseRepository,
            RiskEventRepository eventRepository,
            RiskConfigReleaseRepository configReleaseRepository,
            ObjectMapper objectMapper) {
        this.decisionLogRepository = decisionLogRepository;
        this.caseRepository = caseRepository;
        this.eventRepository = eventRepository;
        this.configReleaseRepository = configReleaseRepository;
        this.objectMapper = objectMapper;
    }

    public RiskDecisionLog getDecision(long id) {
        return decisionLogRepository.findById(id)
                .orElseThrow(() -> new BusinessException("决策记录不存在"));
    }

    public Map<String, Object> replayByOrderId(String orderId) {
        List<RiskDecisionLog> logs = decisionLogRepository.findByOrderId(orderId);
        if (logs.isEmpty()) {
            throw new BusinessException("未找到订单决策记录: " + orderId);
        }
        return buildReplay(logs.get(0), logs);
    }

    public Map<String, Object> replayByTraceId(String traceId) {
        RiskDecisionLog log = decisionLogRepository.findByTraceId(traceId)
                .orElseThrow(() -> new BusinessException("未找到 trace 决策记录: " + traceId));
        return buildReplay(log, List.of(log));
    }

    private Map<String, Object> buildReplay(RiskDecisionLog primary, List<RiskDecisionLog> all) {
        Map<String, Object> result = new HashMap<>();
        result.put("decision", primary);
        result.put("history", all);
        result.put("reasons", parseJson(primary.reasonsJson(), List.of()));
        result.put("versions", parseJson(primary.versionsJson(), Map.of()));
        result.put("featureSnapshot", parseJson(primary.featureSnapshotJson(), Map.of()));
        result.put("market", parseJson(primary.marketJson(), Map.of()));

        Optional<RiskCase> relatedCase = caseRepository.findAll(null).stream()
                .filter(c -> primary.orderId().equals(c.orderId()))
                .findFirst();
        relatedCase.ifPresent(c -> result.put("case", c));

        List<RiskEvent> events = eventRepository.findRecent(200).stream()
                .filter(e -> primary.orderId().equals(e.orderId()))
                .toList();
        result.put("events", events);

        Map<?, ?> versions = (Map<?, ?>) result.get("versions");
        if (versions != null && versions.get("configEpoch") != null) {
            long epoch = ((Number) versions.get("configEpoch")).longValue();
            configReleaseRepository.findAll().stream()
                    .filter(c -> c.configEpoch() == epoch)
                    .findFirst()
                    .ifPresent(c -> result.put("configRelease", c));
        }
        if (!result.containsKey("configRelease")) {
            RiskConfigRelease published = configReleaseRepository.findPublished().orElse(null);
            if (published != null) {
                result.put("configRelease", published);
            }
        }

        result.put("explainable", primary.reasonsJson() != null || primary.versionsJson() != null);
        return result;
    }

    private Object parseJson(String json, Object fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            if (json.trim().startsWith("[")) {
                return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return fallback;
        }
    }
}
