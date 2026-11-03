package com.studyhub.question;

import java.util.List;

import com.studyhub.question.dto.AnswerOptionRequest;
import com.studyhub.question.dto.AnswerOptionResponse;
import com.studyhub.question.dto.QuestionRequest;
import com.studyhub.question.dto.QuestionResponse;
import com.studyhub.quiz.Quiz;
import com.studyhub.quiz.QuizReviewMutationGuard;
import com.studyhub.quiz.QuizSourceType;
import com.studyhub.quiz.AiQuizQuestionValidator;
import com.studyhub.concept.Concept;
import com.studyhub.security.ResourceAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionService {
    private final QuestionRepository questionRepository;
    private final ResourceAccessService resourceAccessService;
    private final QuizReviewMutationGuard reviewGuard;

    public QuestionService(QuestionRepository questionRepository, ResourceAccessService resourceAccessService,
            QuizReviewMutationGuard reviewGuard) {
        this.questionRepository = questionRepository;
        this.resourceAccessService = resourceAccessService;
        this.reviewGuard = reviewGuard;
    }

    @Transactional
    public QuestionResponse create(Long quizId, QuestionRequest request, Long userId) {
        Quiz quiz = reviewGuard.requireEditable(quizId, userId, request.expectedRevision());
        validate(request);
        if (quiz.getSourceType() == QuizSourceType.AI_GENERATED) AiQuizQuestionValidator.validate(request);
        Question question = new Question(quiz, request.text(), request.type(), request.referenceAnswer());
        question.setConcept(resolveConcept(request.conceptId(), quiz, userId));
        for (AnswerOptionRequest option : request.options()) {
            question.addOption(option.text(), option.correct());
        }
        reviewGuard.contentChanged(quiz);
        return toResponse(questionRepository.saveAndFlush(question));
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> list(Long quizId, Long userId) {
        resourceAccessService.requireQuiz(quizId, userId);
        return questionRepository.findAllByQuizIdOrderByIdAsc(quizId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public QuestionResponse get(Long id, Long userId) {
        return toResponse(requireQuestion(id, userId));
    }

    @Transactional
    public QuestionResponse update(Long id, QuestionRequest request, Long userId) {
        Quiz quiz = requireEditableQuestionQuiz(id, userId, request.expectedRevision());
        Question question = requireQuestion(id, userId);
        validate(request);
        if (quiz.getSourceType() == QuizSourceType.AI_GENERATED) AiQuizQuestionValidator.validate(request);
        question.update(request.text(), request.type(), request.referenceAnswer(), request.options().stream()
                .map(option -> new AnswerOption(question, option.text(), option.correct())).toList());
        question.setConcept(resolveConcept(request.conceptId(), question.getQuiz(), userId));
        if (quiz.getSourceType() == QuizSourceType.AI_GENERATED) question.clearReview();
        reviewGuard.contentChanged(quiz);
        questionRepository.flush();
        return toResponse(question);
    }

    @Transactional
    public void delete(Long id, Long userId, Long expectedRevision) {
        Quiz quiz = requireEditableQuestionQuiz(id, userId, expectedRevision);
        questionRepository.delete(requireQuestion(id, userId));
        reviewGuard.contentChanged(quiz);
    }

    private Quiz requireEditableQuestionQuiz(Long id, Long userId, Long revision) {
        Long quizId = questionRepository.findQuizIdInScope(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
        return reviewGuard.requireEditable(quizId, userId, revision);
    }

    private Question requireQuestion(Long id, Long userId) {
        return questionRepository.findByIdAndQuizCourseWorkspaceOwnerId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
    }

    private void validate(QuestionRequest request) {
        List<AnswerOptionRequest> options = request.options();
        long correctCount = options.stream().filter(AnswerOptionRequest::correct).count();
        boolean hasReferenceAnswer = request.referenceAnswer() != null && !request.referenceAnswer().isBlank();
        boolean valid = switch (request.type()) {
            case MULTIPLE_CHOICE -> request.referenceAnswer() == null && options.size() >= 4 && correctCount >= 1;
            case TRUE_FALSE -> request.referenceAnswer() == null && options.size() == 2 && correctCount == 1;
            case SHORT_ANSWER -> hasReferenceAnswer && options.isEmpty();
        };
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid options for question type");
        }
    }

    private Concept resolveConcept(Long conceptId, Quiz quiz, Long userId) {
        if (conceptId == null) {
            return null;
        }
        Concept concept = quiz.getSourceType() == QuizSourceType.AI_GENERATED
                ? resourceAccessService.requireConceptForUpdate(conceptId, userId)
                : resourceAccessService.requireConcept(conceptId, userId);
        if (!concept.getCourse().getId().equals(quiz.getCourse().getId())){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Concept does not belong to the same course as the quiz");
        }
        return concept;
    }

    private QuestionResponse toResponse(Question question) {
        return new QuestionResponse(question.getId(), question.getQuiz().getId(), question.getText(),
                question.getType(), question.getReferenceAnswer(), question.getOptions().stream()
                        .map(option -> new AnswerOptionResponse(option.getId(), option.getText(), option.isCorrect()))
                        .toList(), question.getConcept() != null ? question.getConcept().getId() : null);
    }
}
