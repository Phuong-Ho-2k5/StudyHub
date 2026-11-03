package com.studyhub.quiz;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyhub.course.Course;
import com.studyhub.quiz.dto.CreateQuizRequest;
import com.studyhub.quiz.dto.QuizResponse;
import com.studyhub.quiz.dto.UpdateQuizRequest;
import com.studyhub.security.ResourceAccessService;

@Service
public class QuizService {
    private final QuizRepository quizRepository;
    private final ResourceAccessService resourceAccessService;
    private final QuizReviewMutationGuard reviewGuard;

    public QuizService(QuizRepository quizRepository, ResourceAccessService resourceAccessService,
            QuizReviewMutationGuard reviewGuard) {
        this.quizRepository = quizRepository;
        this.resourceAccessService = resourceAccessService;
        this.reviewGuard = reviewGuard;
    }

    @Transactional
    public QuizResponse create(Long courseId, CreateQuizRequest request, Long userId) {
        Course course = resourceAccessService.requireCourse(courseId, userId);
        return toResponse(quizRepository.save(new Quiz(request.title(), course)));
    }

    @Transactional(readOnly = true)
    public List<QuizResponse> list(Long courseId, Long userId) {
        resourceAccessService.requireCourse(courseId, userId);
        return quizRepository.findAllByCourseIdOrderByIdAsc(courseId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public QuizResponse get(Long quizId, Long userId) {
        return toResponse(resourceAccessService.requireQuiz(quizId, userId));
    }

    @Transactional
    public QuizResponse update(Long quizId, UpdateQuizRequest request, Long userId) {
        // Lock before loading the entity elsewhere in the persistence context.
        Quiz quiz = quizRepository.findForUpdateByIdAndCourseWorkspaceOwnerId(quizId, userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Quiz not found"));
        if (quiz.getSourceType() == QuizSourceType.AI_GENERATED && request.status() != quiz.getStatus()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "AI Quiz state changes require the review workflow");
        }
        if (quiz.getSourceType() == QuizSourceType.MANUAL && request.status() == QuizStatus.CONFIRMED) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "CONFIRMED is reserved for AI Quiz review");
        }
        if (quiz.getSourceType() == QuizSourceType.AI_GENERATED) {
            QuizReviewService.requireRevision(quiz, request.expectedRevision());
            QuizReviewService.requireState(quiz, QuizStatus.DRAFT, QuizStatus.CONFIRMED);
        }
        quiz.setTitle(request.title());
        if (quiz.getSourceType() == QuizSourceType.AI_GENERATED) reviewGuard.contentChanged(quiz);
        else quiz.setStatus(request.status());
        return toResponse(quiz);
    }

    @Transactional
    public void delete(Long quizId, Long userId) {
        quizRepository.delete(resourceAccessService.requireQuiz(quizId, userId));
    }

    private QuizResponse toResponse(Quiz quiz) {
        return new QuizResponse(quiz.getId(), quiz.getCourse().getId(), quiz.getTitle(),
                quiz.getStatus(), quiz.getSourceType());
    }
}
