package com.studyhub.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 500) String description
){}
