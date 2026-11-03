package com.studyhub.quiz;

import com.studyhub.ai.quiz.QuizGenerationRepository;
import com.studyhub.question.*;
import com.studyhub.question.dto.*;
import com.studyhub.quiz.dto.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuizReviewService {
    private final QuizRepository quizzes;
    private final QuestionRepository questions;
    private final QuizGenerationRepository generations;

    public QuizReviewService(QuizRepository quizzes, QuestionRepository questions, QuizGenerationRepository generations) {
        this.quizzes = quizzes; this.questions = questions; this.generations = generations;
    }
    // A single parent lock also gives the read view a coherent revision and question set.
    @Transactional
    public QuizReviewResponse get(Long quizId, Long userId) { return response(lockAi(quizId, userId)); }

    @Transactional
    public QuizReviewResponse reviewQuestion(Long quizId, Long questionId, long revision, Long userId) {
        Quiz quiz = lockAi(quizId, userId);
        Question question = requireQuestion(quizId, questionId);
        requireRevision(quiz, revision);
        requireState(quiz, QuizStatus.DRAFT);
        if (!AiQuizQuestionValidator.valid(question)) throw conflict("Question is invalid");
        if (question.getReviewedAt() == null) question.review(userId);
        return response(quiz);
    }
    @Transactional
    public QuizReviewResponse unreviewQuestion(Long quizId, Long questionId, long revision, Long userId) {
        Quiz quiz = lockAi(quizId, userId);
        Question question = requireQuestion(quizId, questionId);
        requireRevision(quiz, revision);
        requireState(quiz, QuizStatus.DRAFT, QuizStatus.CONFIRMED);
        question.clearReview(); quiz.contentChanged();
        return response(quiz);
    }
    @Transactional
    public QuizReviewResponse confirm(Long quizId, long revision, Long userId) {
        Quiz quiz = lockAi(quizId, userId); requireRevision(quiz, revision);
        requireState(quiz, QuizStatus.DRAFT, QuizStatus.CONFIRMED);
        requireComplete(quizId);
        if (quiz.getStatus() != QuizStatus.CONFIRMED) quiz.confirm(userId);
        return response(quiz);
    }
    @Transactional
    public QuizReviewResponse publish(Long quizId, long revision, Long userId) {
        Quiz quiz = lockAi(quizId, userId); requireRevision(quiz, revision);
        requireState(quiz, QuizStatus.CONFIRMED, QuizStatus.PUBLISHED);
        requireComplete(quizId);
        if (quiz.getStatus() != QuizStatus.PUBLISHED) quiz.publish();
        return response(quiz);
    }
    @Transactional
    public QuizReviewResponse archive(Long quizId, long revision, Long userId) {
        Quiz quiz = lockAi(quizId, userId); requireRevision(quiz, revision);
        quiz.setStatus(QuizStatus.ARCHIVED);
        return response(quiz);
    }
    private Quiz lockAi(Long id, Long owner) {
        Quiz quiz = quizzes.findForUpdateByIdAndCourseWorkspaceOwnerId(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));
        if (quiz.getSourceType() != QuizSourceType.AI_GENERATED) throw conflict("Review requires an AI Quiz");
        return quiz;
    }
    private Question requireQuestion(Long quizId, Long questionId) {
        return questions.findById(questionId).filter(q -> q.getQuiz().getId().equals(quizId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
    }
    public static void requireRevision(Quiz quiz, Long revision) {
        if (revision == null || revision < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedRevision is required for AI Quiz mutations");
        if (quiz.getContentRevision() != revision) throw conflict("Quiz content changed; reload its review");
    }
    public static void requireState(Quiz quiz, QuizStatus... states) {
        if (Arrays.stream(states).noneMatch(s -> s == quiz.getStatus())) throw conflict("Operation is not allowed in this Quiz state");
    }
    private void requireComplete(Long quizId) {
        if (!complete(questions.findAllByQuizIdOrderByIdAsc(quizId))) throw conflict("Every current question must be valid and reviewed");
    }
    private boolean complete(List<Question> values) {
        return !values.isEmpty() && values.stream().allMatch(q -> q.getReviewedAt() != null && AiQuizQuestionValidator.valid(q));
    }
    private QuizReviewResponse response(Quiz quiz) {
        var current = questions.findAllByQuizIdOrderByIdAsc(quiz.getId());
        var snapshot = generations.findSnapshot(quiz.getId());
        var originals = new HashMap<Long, QuestionResponse>();
        var evidence = new HashMap<Long, List<com.studyhub.ai.dto.CourseCitationResponse>>();
        snapshot.ifPresent(s -> {
            s.questions().forEach(q -> originals.put(q.id(), q));
            s.generation().evidence().forEach(e -> evidence.put(e.questionId(), e.citations()));
        });
        var values = current.stream().map(q -> {
            var question = toQuestion(q);
            var original = originals.get(q.getId());
            return new QuizReviewResponse.ReviewedQuestion(question, q.getReviewedBy(), instant(q.getReviewedAt()),
                    original == null ? "NONE" : "ORIGINAL_GENERATION", !sameContent(question, original),
                    evidence.getOrDefault(q.getId(), List.of()));
        }).toList();
        boolean ready = complete(current);
        Boolean sourceCurrent = snapshot.isEmpty() ? null : generations.getGeneration(
                quiz.getId(), quiz.getCourse().getWorkspace().getOwner().getId()).sourceCurrent();
        return new QuizReviewResponse(new QuizResponse(quiz.getId(), quiz.getCourse().getId(), quiz.getTitle(), quiz.getStatus(), quiz.getSourceType()),
                quiz.getContentRevision(), current.size(), (int) current.stream().filter(q -> q.getReviewedAt() != null).count(),
                quiz.getStatus() == QuizStatus.DRAFT && ready, quiz.getStatus() == QuizStatus.CONFIRMED && ready,
                quiz.getConfirmedBy(), instant(quiz.getConfirmedAt()), instant(quiz.getPublishedAt()), sourceCurrent, values);
    }
    private static boolean sameContent(QuestionResponse q, QuestionResponse original) {
        if (original == null) return false;
        return Objects.equals(q.text(), original.text()) && q.type() == original.type()
            && Objects.equals(q.referenceAnswer(), original.referenceAnswer()) && Objects.equals(q.conceptId(), original.conceptId())
            && q.options().stream().map(o -> new AnswerOptionRequest(o.text(), o.correct())).toList()
                .equals(original.options().stream().map(o -> new AnswerOptionRequest(o.text(), o.correct())).toList());
    }
    public static QuestionResponse toQuestion(Question q) {
        return new QuestionResponse(q.getId(), q.getQuiz().getId(), q.getText(), q.getType(), q.getReferenceAnswer(),
                q.getOptions().stream().map(o -> new AnswerOptionResponse(o.getId(), o.getText(), o.isCorrect())).toList(),
                q.getConcept() == null ? null : q.getConcept().getId());
    }
    private static Instant instant(Timestamp time) { return time == null ? null : time.toInstant(); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
