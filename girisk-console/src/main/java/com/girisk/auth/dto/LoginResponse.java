package com.girisk.auth.dto;

import java.util.List;

public record LoginResponse(
        String token,
        String username,
        String displayName,
        String role,
        List<String> roles,
        List<String> permissions,
        String operatorScope
) {}
