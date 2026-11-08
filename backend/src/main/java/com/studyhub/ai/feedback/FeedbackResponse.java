package com.studyhub.ai.feedback;

import java.time.Instant;

public record FeedbackResponse(Long id, Long messageId, FeedbackRating rating, String note, Instant createdAt, Instant updatedAt) {
    public static FeedbackResponse from(AIFeedback feedback) {
        return new FeedbackResponse(feedback.getId(),feedback.getMessageId(),feedback.getRating(),feedback.getNote(),feedback.getCreatedAt(),feedback.getUpdatedAt());
    }
}
