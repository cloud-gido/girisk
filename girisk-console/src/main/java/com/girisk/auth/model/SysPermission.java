package com.girisk.auth.model;

import java.time.LocalDateTime;

public record SysPermission(
        Long id,
        String code,
        String name,
        String module,
        String description,
        LocalDateTime createdAt
) {}
