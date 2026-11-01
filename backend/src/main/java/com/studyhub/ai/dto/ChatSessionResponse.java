package com.studyhub.ai.dto;

import java.time.Instant;

public record ChatSessionResponse(Long id, Long courseId, String title, Instant createdAt, Instant updatedAt) {}
