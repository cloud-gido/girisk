package com.girisk.auth.dto;

import java.util.List;

public record IamRoleView(
        Long id,
        String code,
        String name,
        boolean builtin,
        String description,
        List<String> permissions
) {}
