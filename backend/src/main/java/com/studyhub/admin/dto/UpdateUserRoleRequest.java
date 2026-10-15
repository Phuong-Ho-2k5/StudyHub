package com.studyhub.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateUserRoleRequest(
        @NotNull @Pattern(regexp = "USER|ADMIN") String role
) {}
