package com.studyhub.quiz.dto;

public record QuestionResult(
        Long questionId,
        Boolean isCorrect
) {}