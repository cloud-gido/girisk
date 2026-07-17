package com.girisk.auth.model;

import java.time.LocalDateTime;

public record SysUser(
        Long id,
        String username,
        String passwordHash,
        String displayName,
        String role,
        boolean enabled,
        LocalDateTime createdAt
) {}
