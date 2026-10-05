package com.studyhub.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnswerOptionRequest(
        @NotBlank @Size(max = 500) String text,
        @NotNull Boolean correct) {}
