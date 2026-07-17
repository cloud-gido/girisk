package com.girisk.api;

import com.girisk.auth.AuthService;
import com.girisk.auth.dto.LoginRequest;
import com.girisk.auth.dto.LoginResponse;
import com.girisk.auth.dto.UserProfile;
import com.girisk.common.exception.BusinessException;
import com.girisk.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
}
