package com.studyhub.user.dto;

public record UserStatisticsResponse(
        long completedStudySessions,
        long totalStudySeconds,
        long quizAttemptCount,
        double averageQuizScore) {
}
