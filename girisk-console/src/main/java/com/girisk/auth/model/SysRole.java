package com.girisk.auth.model;

import java.time.LocalDateTime;

public record SysRole(
        Long id,
        String code,
        String name,
        boolean builtin,
        String description,
        LocalDateTime createdAt
) {}
