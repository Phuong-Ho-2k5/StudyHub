package com.studyhub.quiz.dto;

import com.studyhub.quiz.QuizStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateQuizRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull QuizStatus status) {}
