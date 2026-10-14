package com.studyhub.quiz;

public record QuizSubmittedEvent(Long userId, Long quizId, double questionPercentage) {}
