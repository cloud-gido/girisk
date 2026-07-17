package com.girisk.api;

import com.girisk.case_.model.RiskCase;
import com.girisk.case_.repository.RiskCaseRepository;
import com.girisk.case_.service.CaseReviewService;
import com.girisk.common.dto.ApiResponse;
import com.girisk.common.exception.BusinessException;
import com.girisk.gateway.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final RiskCaseRepository repository;
    private final CaseReviewService reviewService;
    private final TenantContext tenantContext;
    private final boolean tenantEnforce;

    public CaseController(
            RiskCaseRepository repository,
            CaseReviewService reviewService,
            TenantContext tenantContext,
            @Value("${girisk.tenant.enforce:false}") boolean tenantEnforce) {
        this.repository = repository;
        this.reviewService = reviewService;
        this.tenantContext = tenantContext;
        this.tenantEnforce = tenantEnforce;
    }

    @GetMapping
    public ApiResponse<List<RiskCase>> list(
            @RequestParam(required = false) String status,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorHeader) {
        String op = tenantEnforce ? firstNonBlank(operatorHeader, tenantContext.getOperatorId()) : operatorHeader;
        return ApiResponse.ok(repository.findAll(status, op));
    }

    @GetMapping("/{id}")
    public ApiResponse<RiskCase> detail(@PathVariable long id) {
        RiskCase c = repository.findById(id).orElse(null);
        if (c != null) {
            assertTenant(c.operatorId());
        }
        return ApiResponse.ok(c);
    }

    @PostMapping("/{id}/review")
    public ApiResponse<RiskCase> review(@PathVariable long id, @RequestBody Map<String, String> body) {
        RiskCase existing = repository.findById(id).orElseThrow(() -> new BusinessException("工单不存在"));
        assertTenant(existing.operatorId());
        String decision = body.get("decision");
        String comment = body.getOrDefault("comment", "");
        String assignee = body.getOrDefault("assignee", "admin");
        return ApiResponse.ok(reviewService.review(id, decision, comment, assignee));
    }

    private void assertTenant(String operatorId) {
        if (!tenantEnforce) return;
        String op = tenantContext.getOperatorId();
        if (op != null && operatorId != null && !op.equals(operatorId)) {
            throw new BusinessException("无权访问其他租户工单");
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
