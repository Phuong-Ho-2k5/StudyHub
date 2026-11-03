package com.studyhub.quiz;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class QuizReviewMutationGuard {
    private final QuizRepository quizzes;
    public QuizReviewMutationGuard(QuizRepository quizzes) { this.quizzes = quizzes; }
    /** Must be called inside the mutation transaction, before loading child entities. */
    public Quiz requireEditable(Long quizId, Long userId, Long revision) {
        Quiz quiz = quizzes.findForUpdateByIdAndCourseWorkspaceOwnerId(quizId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));
        if (quiz.getSourceType() == QuizSourceType.AI_GENERATED) {
            QuizReviewService.requireRevision(quiz, revision);
            QuizReviewService.requireState(quiz, QuizStatus.DRAFT, QuizStatus.CONFIRMED);
        }
        return quiz;
    }
    public void contentChanged(Quiz quiz) {
        if (quiz.getSourceType() == QuizSourceType.AI_GENERATED) quiz.contentChanged();
    }
}
