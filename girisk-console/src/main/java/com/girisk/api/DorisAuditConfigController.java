package com.girisk.api;

import com.girisk.audit.DorisAuditConfigRequest;
import com.girisk.audit.DorisAuditDataSourceManager;
import com.girisk.audit.OpsAuditService;
import com.girisk.common.dto.ApiResponse;
import com.girisk.sports.service.ScopeDutyAuth;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit/doris")
public class DorisAuditConfigController {

    private final DorisAuditDataSourceManager manager;
    private final ScopeDutyAuth dutyAuth;
    private final OpsAuditService audit;

    public DorisAuditConfigController(
            DorisAuditDataSourceManager manager, ScopeDutyAuth dutyAuth, OpsAuditService audit) {
        this.manager = manager;
        this.dutyAuth = dutyAuth;
        this.audit = audit;
    }

    /** 状态：audit:read 可读；ADMIN 可见完整 jdbcUrl。 */
    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<Map<String, Object>> get() {
        return ApiResponse.ok(manager.statusView(dutyAuth.isAdmin()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> put(@RequestBody DorisAuditConfigRequest body) {
        dutyAuth.requireAdmin();
        if (body == null) {
            return ApiResponse.fail("body required");
        }
        try {
            Map<String, Object> status =
                    manager.updateAndApply(body.toSettings(manager.effectiveSettings()));
            audit.record(
                    OpsAuditService.DUTY_AUDIT_DORIS_CONFIG,
                    "更新 Doris 审计数据源",
                    "by="
                            + dutyAuth.currentUsername()
                            + " enabled="
                            + status.get("enabled")
                            + " available="
                            + status.get("available")
                            + " host="
                            + status.get("host")
                            + ":"
                            + status.get("port")
                            + "/"
                            + status.get("database")
                            + " decisionTable="
                            + status.get("decisionTable")
                            + " configTable="
                            + status.get("configTable"));
            return ApiResponse.ok(status);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> test(@RequestBody(required = false) DorisAuditConfigRequest body) {
        dutyAuth.requireAdmin();
        DorisAuditConfigRequest req = body == null ? new DorisAuditConfigRequest() : body;
        try {
            return ApiResponse.ok(manager.testConnection(req.toSettings(manager.effectiveSettings())));
        } catch (IllegalArgumentException e) {
            return ApiResponse.ok(Map.of("ok", false, "message", e.getMessage()));
        }
    }
}
