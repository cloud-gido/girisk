package com.girisk.api;

import com.girisk.common.dto.ApiResponse;
import com.girisk.decision.model.RiskDecisionLog;
import com.girisk.decision.repository.RiskDecisionLogRepository;
import com.girisk.decision.service.DecisionReplayService;
import com.girisk.event.model.RiskEvent;
import com.girisk.event.repository.RiskEventRepository;
import com.girisk.gateway.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('audit:read')")
public class AuditController {

    private final RiskDecisionLogRepository decisionLogRepository;
    private final RiskEventRepository eventRepository;
    private final DecisionReplayService replayService;
    private final TenantContext tenantContext;
    private final boolean tenantEnforce;

    public AuditController(
            RiskDecisionLogRepository decisionLogRepository,
            RiskEventRepository eventRepository,
            DecisionReplayService replayService,
            TenantContext tenantContext,
            @Value("${girisk.tenant.enforce:false}") boolean tenantEnforce) {
        this.decisionLogRepository = decisionLogRepository;
        this.eventRepository = eventRepository;
        this.replayService = replayService;
        this.tenantContext = tenantContext;
        this.tenantEnforce = tenantEnforce;
    }

    @GetMapping("/decisions")
    public ApiResponse<List<RiskDecisionLog>> decisions(
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorHeader,
            @RequestParam(required = false) String operatorId) {
        String op = operatorId != null ? operatorId : operatorHeader;
        if (op == null && tenantEnforce) {
            op = tenantContext.getOperatorId();
        }
        if (op != null && !op.isBlank()) {
            return ApiResponse.ok(decisionLogRepository.findRecentByOperator(op, limit));
        }
        return ApiResponse.ok(decisionLogRepository.findRecent(limit));
    }

    @GetMapping("/decisions/{id}")
    public ApiResponse<RiskDecisionLog> decisionDetail(@PathVariable long id) {
        RiskDecisionLog log = replayService.getDecision(id);
        assertTenant(log.operatorId());
        return ApiResponse.ok(log);
    }

    @GetMapping("/decisions/by-order/{orderId}")
    public ApiResponse<List<RiskDecisionLog>> decisionsByOrder(@PathVariable String orderId) {
        List<RiskDecisionLog> logs = decisionLogRepository.findByOrderId(orderId);
        if (tenantEnforce) {
            String op = tenantContext.getOperatorId();
            if (op != null) {
                logs = logs.stream().filter(l -> op.equals(l.operatorId())).toList();
            }
        }
        return ApiResponse.ok(logs);
    }

    @GetMapping("/replay/order/{orderId}")
    public ApiResponse<Map<String, Object>> replayByOrder(@PathVariable String orderId) {
        Map<String, Object> replay = replayService.replayByOrderId(orderId);
        if (replay.get("decision") instanceof RiskDecisionLog log) {
            assertTenant(log.operatorId());
        }
        return ApiResponse.ok(replay);
    }

    @GetMapping("/replay/trace/{traceId}")
    public ApiResponse<Map<String, Object>> replayByTrace(@PathVariable String traceId) {
        Map<String, Object> replay = replayService.replayByTraceId(traceId);
        if (replay.get("decision") instanceof RiskDecisionLog log) {
            assertTenant(log.operatorId());
        }
        return ApiResponse.ok(replay);
    }

    @GetMapping("/events")
    public ApiResponse<List<RiskEvent>> events(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(eventRepository.findRecent(limit));
    }

    private void assertTenant(String operatorId) {
        if (!tenantEnforce) return;
        String op = tenantContext.getOperatorId();
        if (op != null && operatorId != null && !op.equals(operatorId)) {
            throw new com.girisk.common.exception.BusinessException("无权访问其他租户数据");
        }
    }
}
