package com.studyhub.quiz.dto;

import java.util.List;

public record QuizResult(
        Long quizId,
        int totalQuestions,
        int gradedCount,
        int correctCount,
        int incorrectCount,
        int pendingCount,
        Double questionPercentage,
        List<QuestionResult> questionResults
) {}