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
import com.studyhub.security.ResourceAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuizSubmissionService {
    private final ResourceAccessService resourceAccessService;
    private final QuestionRepository questionRepository;

    public QuizSubmissionService(ResourceAccessService resourceAccessService, QuestionRepository questionRepository) {
        this.resourceAccessService = resourceAccessService;
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public void validate(Long quizId, SubmitQuizRequest request, Long userId) {
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

    private void invalid(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
