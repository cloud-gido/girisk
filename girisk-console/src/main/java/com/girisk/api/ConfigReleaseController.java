package com.girisk.api;

import com.girisk.common.dto.ApiResponse;
import com.girisk.configcenter.model.RiskConfigRelease;
import com.girisk.configcenter.service.ConfigReleaseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config/releases")
@PreAuthorize("hasAuthority('config:manage')")
public class ConfigReleaseController {

    private final ConfigReleaseService service;

    public ConfigReleaseController(ConfigReleaseService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<RiskConfigRelease>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/current")
    public ApiResponse<RiskConfigRelease> current() {
        return ApiResponse.ok(service.currentPublished());
    }

    @GetMapping("/{id}")
    public ApiResponse<RiskConfigRelease> get(@PathVariable long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<RiskConfigRelease> create(@RequestBody Map<String, Object> body) {
        String actor = String.valueOf(body.getOrDefault("createdBy", "admin"));
        return ApiResponse.ok(service.createDraft(body, actor));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<RiskConfigRelease> submit(@PathVariable long id, @RequestBody(required = false) Map<String, String> body) {
        String actor = body != null ? body.getOrDefault("actor", "admin") : "admin";
        return ApiResponse.ok(service.submit(id, actor));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<RiskConfigRelease> approve(@PathVariable long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(service.approve(id, body.getOrDefault("actor", "reviewer"), body.get("approvalTicket")));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<RiskConfigRelease> reject(@PathVariable long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(service.reject(id, body.getOrDefault("actor", "reviewer"), body.get("reason")));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<RiskConfigRelease> publish(@PathVariable long id, @RequestBody(required = false) Map<String, String> body) {
        String actor = body != null ? body.getOrDefault("actor", "admin") : "admin";
        return ApiResponse.ok(service.publish(id, actor));
    }
}
