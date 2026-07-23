package com.girisk.api;

import com.girisk.common.dto.ApiResponse;
import com.girisk.gateway.OrderRiskStateService;
import com.girisk.gateway.RiskDecisionGateway;
import com.girisk.gateway.RiskDecisionRequest;
import com.girisk.gateway.RiskDecisionResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/girisk")
@PreAuthorize("hasAuthority('sandbox:use')")
public class RiskDecideController {

    private final RiskDecisionGateway gateway;
    private final OrderRiskStateService orderRiskStateService;

    public RiskDecideController(RiskDecisionGateway gateway, OrderRiskStateService orderRiskStateService) {
        this.gateway = gateway;
        this.orderRiskStateService = orderRiskStateService;
    }

    /** 唯一正式决策入口。 */
    @PostMapping("/decide")
    public ApiResponse<RiskDecisionResponse> decide(@Valid @RequestBody RiskDecisionRequest request) {
        return ApiResponse.ok(gateway.decide(request));
    }

    @PostMapping("/orders/{orderId}/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable String orderId) {
        return ApiResponse.ok(orderRiskStateService.confirm(orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable String orderId) {
        return ApiResponse.ok(orderRiskStateService.cancel(orderId));
    }

    @PostMapping("/orders/{orderId}/settle")
    public ApiResponse<Map<String, Object>> settle(
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, Object> body) {
        Long pnl = null;
        if (body != null && body.get("settlePnlCents") != null) {
            pnl = Long.valueOf(String.valueOf(body.get("settlePnlCents")));
        }
        return ApiResponse.ok(orderRiskStateService.settle(orderId, pnl));
    }

    @GetMapping("/orders/{orderId}/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable String orderId) {
        return ApiResponse.ok(orderRiskStateService.status(orderId));
    }
}
