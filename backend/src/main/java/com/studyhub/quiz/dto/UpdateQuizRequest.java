package com.studyhub.quiz.dto;

import com.studyhub.quiz.QuizStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateQuizRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull QuizStatus status,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Required for AI Quiz: contentRevision from GET review; optional for MANUAL", minimum = "0")
        @tools.jackson.databind.annotation.JsonDeserialize(using = RevisionDeserializer.class)
        @jakarta.validation.constraints.PositiveOrZero Long expectedRevision) {
    public UpdateQuizRequest(String title, QuizStatus status) { this(title, status, null); }
}
