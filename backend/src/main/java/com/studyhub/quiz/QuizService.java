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

    public QuizService(QuizRepository quizRepository, ResourceAccessService resourceAccessService) {
        this.quizRepository = quizRepository;
        this.resourceAccessService = resourceAccessService;
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
        Quiz quiz = resourceAccessService.requireQuiz(quizId, userId);
        quiz.setTitle(request.title());
        quiz.setStatus(request.status());
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
