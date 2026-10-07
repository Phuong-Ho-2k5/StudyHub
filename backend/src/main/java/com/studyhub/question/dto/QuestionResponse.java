package com.studyhub.question.dto;

import java.util.List;

import com.studyhub.question.QuestionType;

public record QuestionResponse(Long id, Long quizId, String text, QuestionType type, String referenceAnswer,
        List<AnswerOptionResponse> options, Long conceptId) {}
