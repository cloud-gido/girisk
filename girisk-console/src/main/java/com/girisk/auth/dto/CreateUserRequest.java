package com.girisk.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(min = 6, max = 64) String password,
        @NotBlank @Size(max = 64) String displayName,
        @NotBlank String role,
        List<String> roles
) {}
