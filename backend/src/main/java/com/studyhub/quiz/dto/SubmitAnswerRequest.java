package com.studyhub.quiz.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SubmitAnswerRequest(
        @NotNull @Positive Long questionId,
        List<@NotNull @Positive Long> answerOptionIds,
        @Size(max = 4000) String answerText) {}
