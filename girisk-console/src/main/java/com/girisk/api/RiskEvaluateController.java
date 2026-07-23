package com.girisk.api;

import com.girisk.common.dto.ApiResponse;
import com.girisk.common.dto.RiskEvaluateRequest;
import com.girisk.common.dto.RiskEvaluateResponse;
import com.girisk.common.enums.RiskDecision;
import com.girisk.gateway.RiskDecisionGateway;
import com.girisk.gateway.RiskDecisionRequest;
import com.girisk.gateway.RiskDecisionResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @deprecated 请使用 {@code POST /api/v1/girisk/decide}。本接口降级为适配层。
 */
@Deprecated
@RestController
@RequestMapping("/api/v1/girisk")
@PreAuthorize("hasAuthority('sandbox:use')")
public class RiskEvaluateController {

    private final RiskDecisionGateway gateway;

    public RiskEvaluateController(RiskDecisionGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/evaluate")
    public ApiResponse<RiskEvaluateResponse> evaluate(@Valid @RequestBody RiskEvaluateRequest request) {
        RiskDecisionResponse resp = gateway.decide(RiskDecisionRequest.fromLegacy(request), "LEGACY_EVALUATE");
        return ApiResponse.ok(toLegacy(resp));
    }

    private static RiskEvaluateResponse toLegacy(RiskDecisionResponse resp) {
        List<String> hits = resp.reasons() == null ? List.of()
                : resp.reasons().stream().map(r -> r.ruleId()).toList();
        RiskDecision decision = resp.decision() == RiskDecision.LIMIT ? RiskDecision.REJECT : resp.decision();
        return new RiskEvaluateResponse(
                resp.requestId(), resp.orderId(), decision, resp.riskScore(), resp.riskLevel(),
                hits, resp.reason(), resp.strategyCode(), resp.latencyMs(), resp.caseNo());
    }
}
