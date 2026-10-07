package com.studyhub.quiz;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.studyhub.question.Question;
import com.studyhub.question.QuestionRepository;
import com.studyhub.question.QuestionType;
import com.studyhub.quiz.dto.SubmitAnswerRequest;
import com.studyhub.quiz.dto.SubmitQuizRequest;
import com.studyhub.quiz.dto.QuizResult;
import com.studyhub.quiz.dto.QuestionResult;
import com.studyhub.security.ResourceAccessService;
import com.studyhub.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuizSubmissionService {
    private final ResourceAccessService resourceAccessService;
    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final UserRepository userRepository;

    public QuizSubmissionService(ResourceAccessService resourceAccessService, QuestionRepository questionRepository,
            QuizAttemptRepository quizAttemptRepository, UserRepository userRepository) {
        this.resourceAccessService = resourceAccessService;
        this.questionRepository = questionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public QuizResult submitQuiz(Long quizId, SubmitQuizRequest request, Long userId) {
        Quiz quiz = resourceAccessService.requireQuiz(quizId, userId);
        if (quiz.getStatus() != QuizStatus.PUBLISHED) {
            invalid("Quiz is not published");
        }

        List<Question> questions = questionRepository.findAllByQuizIdOrderByIdAsc(quizId);
        if (questions.isEmpty() || request.answers().size() != questions.size()) {
            invalid("Every question must be answered exactly once");
        }

        Map<Long, Question> questionsById = new HashMap<>();
        for (Question question : questions) {
            questionsById.put(question.getId(), question);
        }
        Set<Long> seenQuestions = new HashSet<>();
        for (SubmitAnswerRequest answer : request.answers()) {
            Question question = questionsById.get(answer.questionId());
            if (question == null || !seenQuestions.add(answer.questionId())) {
                invalid("Unknown or duplicate question");
            }
            validateAnswer(question, answer);
        }
        List<QuestionResult> results = new java.util.ArrayList<>();
        int correctCount = 0;
        int gradedCount = 0;
        int incorrectCount = 0;
        int pendingCount = 0;

        for (SubmitAnswerRequest answer : request.answers()) {
            Question question = questionsById.get(answer.questionId());
            Boolean isCorrect = isCorrect(question, answer);
            if (isCorrect == null) {
                pendingCount++;
            } else if (isCorrect) {
                correctCount++;
                gradedCount++;
            } else {
                incorrectCount++;
                gradedCount++;
            }
            results.add(new QuestionResult(question.getId(), isCorrect));
        }
        int totalQuestions = questions.size();
        if (totalQuestions == 0) {
            invalid("Quiz has no questions");
        }
        double questionPercentage = (double) correctCount / totalQuestions * 100;
        quizAttemptRepository.save(new QuizAttempt(quiz, userRepository.getReferenceById(userId),
                totalQuestions, gradedCount, correctCount, incorrectCount, pendingCount, questionPercentage));
        return new QuizResult(quizId, totalQuestions, gradedCount, correctCount, incorrectCount, pendingCount, questionPercentage, List.copyOf(results));
    }

    private void validateAnswer(Question question, SubmitAnswerRequest answer) {
        if (question.getType() == QuestionType.SHORT_ANSWER) {
            if (answer.answerText() == null || answer.answerText().isBlank()
                    || answer.answerOptionIds() != null) {
                invalid("Short answer requires answerText only");
            }
            return;
        }

        List<Long> selected = answer.answerOptionIds();
        if (answer.answerText() != null || selected == null || selected.isEmpty()
                || question.getType() == QuestionType.TRUE_FALSE && selected.size() != 1) {
            invalid("Question requires valid answerOptionIds only");
        }
        Set<Long> validOptionIds = new HashSet<>();
        question.getOptions().forEach(option -> validOptionIds.add(option.getId()));
        if (new HashSet<>(selected).size() != selected.size() || !validOptionIds.containsAll(selected)) {
            invalid("Unknown or duplicate answer option");
        }
    }

    private Boolean isCorrect(Question question, SubmitAnswerRequest answer) {
        if (question.getType() == QuestionType.SHORT_ANSWER) {
            return null;
        }
        Set<Long> correctOptionIds = new HashSet<>();
        question.getOptions().stream().filter(option -> option.isCorrect()).forEach(option -> correctOptionIds.add(option.getId()));
        Set<Long> selectedOptionIds = new HashSet<>(answer.answerOptionIds());
        return correctOptionIds.equals(selectedOptionIds);
    }

    private void invalid(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
