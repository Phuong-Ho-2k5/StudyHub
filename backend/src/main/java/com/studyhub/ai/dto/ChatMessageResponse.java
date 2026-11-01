package com.studyhub.ai.dto;

import com.studyhub.ai.ChatMessageRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record ChatMessageResponse(Long id, Long sessionId, ChatMessageRole role, String content,
        @Schema(nullable=true) String status, @Schema(nullable=true) String model,
        @Schema(nullable=true) String promptVersion, List<CourseCitationResponse> citations, Instant createdAt) {}
