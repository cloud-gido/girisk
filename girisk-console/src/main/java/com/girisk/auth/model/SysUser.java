package com.girisk.auth.model;

import java.time.LocalDateTime;

public record SysUser(
        Long id,
        String username,
        String passwordHash,
        String displayName,
        String role,
        boolean enabled,
        String operatorScope,
        LocalDateTime createdAt
) {
    /** 兼容旧构造（无 operatorScope） */
    public SysUser(Long id, String username, String passwordHash, String displayName,
                   String role, boolean enabled, LocalDateTime createdAt) {
        this(id, username, passwordHash, displayName, role, enabled, "*", createdAt);
    }
}
