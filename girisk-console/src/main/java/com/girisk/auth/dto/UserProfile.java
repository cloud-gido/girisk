package com.girisk.auth.dto;

import java.util.List;

public record UserProfile(
        String username,
        String displayName,
        String role,
        List<String> roles,
        List<String> permissions,
        String operatorScope
) {}
