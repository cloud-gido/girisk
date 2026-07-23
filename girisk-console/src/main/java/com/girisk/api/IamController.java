package com.girisk.api;

import com.girisk.auth.IamService;
import com.girisk.auth.dto.CreateUserRequest;
import com.girisk.auth.dto.IamRoleView;
import com.girisk.auth.dto.IamUserView;
import com.girisk.auth.dto.ResetPasswordRequest;
import com.girisk.auth.dto.UpdateRolePermissionsRequest;
import com.girisk.auth.dto.UpdateUserRequest;
import com.girisk.auth.model.SysPermission;
import com.girisk.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/iam")
@PreAuthorize("hasAuthority('iam:manage')")
public class IamController {

    private final IamService iamService;

    public IamController(IamService iamService) {
        this.iamService = iamService;
    }

    @GetMapping("/users")
    public ApiResponse<List<IamUserView>> listUsers() {
        return ApiResponse.ok(iamService.listUsers());
    }

    @GetMapping("/users/{id}")
    public ApiResponse<IamUserView> getUser(@PathVariable long id) {
        return ApiResponse.ok(iamService.getUser(id));
    }

    @PostMapping("/users")
    public ApiResponse<IamUserView> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(iamService.createUser(request));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<IamUserView> updateUser(@PathVariable long id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(iamService.updateUser(id, request));
    }

    @PatchMapping("/users/{id}/enabled")
    public ApiResponse<IamUserView> setEnabled(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ApiResponse.fail("enabled required");
        }
        return ApiResponse.ok(iamService.setEnabled(id, enabled));
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<Map<String, String>> resetPassword(
            @PathVariable long id, @Valid @RequestBody ResetPasswordRequest request) {
        iamService.resetPassword(id, request);
        return ApiResponse.ok(Map.of("status", "ok"));
    }

    @GetMapping("/roles")
    public ApiResponse<List<IamRoleView>> listRoles() {
        return ApiResponse.ok(iamService.listRoles());
    }

    @GetMapping("/permissions")
    public ApiResponse<List<SysPermission>> listPermissions() {
        return ApiResponse.ok(iamService.listPermissions());
    }

    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<IamRoleView> updateRolePermissions(
            @PathVariable long id, @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return ApiResponse.ok(iamService.updateRolePermissions(id, request));
    }
}
