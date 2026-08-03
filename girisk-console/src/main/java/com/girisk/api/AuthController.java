package com.girisk.api;

import com.girisk.auth.AuthService;
import com.girisk.auth.dto.LoginRequest;
import com.girisk.auth.dto.LoginResponse;
import com.girisk.auth.dto.UserProfile;
import com.girisk.common.exception.BusinessException;
import com.girisk.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfile> me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            throw new BusinessException("未登录");
        }
        return ApiResponse.ok(authService.profile(authentication.getName()));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout(HttpServletRequest request) {
        authService.logout(request.getHeader(HttpHeaders.AUTHORIZATION));
        return ApiResponse.ok(Map.of("status", "ok"));
    }

    @PostMapping("/change-password")
    public ApiResponse<Map<String, String>> changePassword(
            Authentication authentication,
            @Valid @RequestBody com.girisk.auth.dto.ChangePasswordRequest request) {
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            throw new BusinessException("未登录");
        }
        authService.changePassword(
                authentication.getName(), request.currentPassword(), request.newPassword());
        return ApiResponse.ok(Map.of("status", "ok"));
    }
}
