package com.studyhub.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateUserRoleRequest(
        @Schema(description = "Quyền mới; admin không được tự hạ quyền.", allowableValues = {"USER", "ADMIN"}, example = "USER")
        @NotNull @Pattern(regexp = "USER|ADMIN") String role
) {}
