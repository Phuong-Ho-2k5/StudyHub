package com.studyhub.quiz.dto;

import com.studyhub.ai.dto.CourseCitationResponse;
import com.studyhub.question.dto.QuestionResponse;
import java.time.Instant;
import java.util.List;

public record QuizReviewResponse(QuizResponse quiz, long contentRevision, int totalQuestions,
        int reviewedQuestions, boolean canConfirm, boolean canPublish, Long confirmedBy,
        Instant confirmedAt, Instant publishedAt, Boolean sourceCurrent, List<ReviewedQuestion> questions) {
    public record ReviewedQuestion(QuestionResponse question, Long reviewedBy, Instant reviewedAt,
            String evidenceOrigin, boolean editedSinceGeneration, List<CourseCitationResponse> citations) {}
}
