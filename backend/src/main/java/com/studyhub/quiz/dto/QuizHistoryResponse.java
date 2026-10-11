package com.studyhub.quiz.dto;

import java.time.Instant;

import com.studyhub.quiz.QuizAttempt;

public record QuizHistoryResponse(
        Long attemptId,
        Long quizId,
        int totalQuestions,
        int gradedCount,
        int correctCount,
        int incorrectCount,
        int pendingCount,
        double questionPercentage,
        Instant createdAt
) {
    public static QuizHistoryResponse from(QuizAttempt attempt) {
        return new QuizHistoryResponse(
                attempt.getId(),
                attempt.getQuiz().getId(),
                attempt.getTotalQuestions(),
                attempt.getGradedCount(),
                attempt.getCorrectCount(),
                attempt.getIncorrectCount(),
                attempt.getPendingCount(),
                attempt.getQuestionPercentage(),
                attempt.getCreatedAt().toInstant()
        );
    }
}
