package com.studyhub.quiz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateQuizRequest(@NotBlank @Size(max = 200) String title) {}
