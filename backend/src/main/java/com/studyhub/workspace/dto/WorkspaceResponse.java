package com.studyhub.workspace.dto;

import java.sql.Timestamp;

public record WorkspaceResponse(
    Long id,
    String name,
    String description,
    Timestamp createdAt,
    Timestamp updatedAt
){}