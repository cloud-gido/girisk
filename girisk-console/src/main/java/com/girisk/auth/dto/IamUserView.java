package com.girisk.auth.dto;

import java.util.List;

public record IamUserView(
        Long id,
        String username,
        String displayName,
        String role,
        boolean enabled,
        List<String> roles,
        List<String> permissions,
        String createdAt
) {}
