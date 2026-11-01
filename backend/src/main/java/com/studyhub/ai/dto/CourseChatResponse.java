package com.studyhub.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CourseChatResponse(Long courseId, String question, String status, String answer,
        @Schema(nullable=true) String model, String promptVersion, java.util.List<CourseCitationResponse> citations,
        @Schema(nullable=true) Long sessionId, @Schema(nullable=true) Long userMessageId,
        @Schema(nullable=true) Long assistantMessageId) {}
