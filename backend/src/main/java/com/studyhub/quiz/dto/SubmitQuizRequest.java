package com.studyhub.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SubmitQuizRequest(
        @Schema(description = "Phải chứa đủ các câu trong Quiz, không trùng hoặc thêm câu ngoài Quiz.")
        @NotEmpty List<@NotNull @Valid SubmitAnswerRequest> answers) {}
