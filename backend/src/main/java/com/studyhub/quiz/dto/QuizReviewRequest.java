package com.studyhub.quiz.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

public record QuizReviewRequest(
        @io.swagger.v3.oas.annotations.media.Schema(description = "Current contentRevision from GET review", minimum = "0", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        long expectedRevision) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static QuizReviewRequest parse(JsonNode body) {
        if (!body.isObject() || body.size() != 1 || !body.has("expectedRevision"))
            throw new IllegalArgumentException("Only expectedRevision is required");
        return new QuizReviewRequest(RevisionFields.required(body.get("expectedRevision")));
    }
}
