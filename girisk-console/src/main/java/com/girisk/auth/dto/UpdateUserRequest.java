package com.girisk.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateUserRequest(
        @NotBlank @Size(max = 64) String displayName,
        @NotBlank String role,
        Boolean enabled,
        List<String> roles
) {}
