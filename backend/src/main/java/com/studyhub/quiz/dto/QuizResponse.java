package com.studyhub.quiz.dto;

import com.studyhub.quiz.QuizSourceType;
import com.studyhub.quiz.QuizStatus;

public record QuizResponse(
        Long id,
        Long courseId,
        String title,
        QuizStatus status,
        QuizSourceType sourceType) {}
