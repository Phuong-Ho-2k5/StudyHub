package com.studyhub.quiz.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SubmitQuizRequest(
        @NotEmpty List<@NotNull @Valid SubmitAnswerRequest> answers) {}
