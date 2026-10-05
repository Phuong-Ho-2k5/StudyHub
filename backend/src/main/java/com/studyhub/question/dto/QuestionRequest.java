package com.studyhub.question.dto;

import java.util.List;

import com.studyhub.question.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuestionRequest(
        @NotBlank @Size(max = 500) String text,
        @NotNull QuestionType type,
        @NotEmpty List<@NotNull @Valid AnswerOptionRequest> options) {}
