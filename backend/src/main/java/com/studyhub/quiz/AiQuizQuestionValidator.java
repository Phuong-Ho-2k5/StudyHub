package com.studyhub.quiz;

import com.studyhub.question.Question;
import com.studyhub.question.QuestionType;
import com.studyhub.question.dto.AnswerOptionRequest;
import com.studyhub.question.dto.QuestionRequest;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AiQuizQuestionValidator {
    private AiQuizQuestionValidator() {}
    public static void validate(QuestionRequest q) {
        validate(q.text(), q.type(), q.referenceAnswer(), q.options());
    }
    public static boolean valid(Question q) {
        return valid(q.getText(), q.getType(), q.getReferenceAnswer(), q.getOptions().stream()
                .map(o -> new AnswerOptionRequest(o.getText(), o.isCorrect())).toList());
    }
    private static void validate(String text, QuestionType type, String reference, List<AnswerOptionRequest> options) {
        if (!valid(text, type, reference, options))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI questions require text and four distinct options with exactly one correct answer");
    }
    private static boolean valid(String text, QuestionType type, String reference, List<AnswerOptionRequest> options) {
        return text != null && !normalized(text).isBlank() && text.length() <= 500
            && type == QuestionType.MULTIPLE_CHOICE && reference == null && options.size() == 4
            && options.stream().allMatch(o -> o.text() != null && !normalized(o.text()).isBlank() && o.text().length() <= 500 && o.correct() != null)
            && options.stream().filter(o -> Boolean.TRUE.equals(o.correct())).count() == 1
            && options.stream().map(o -> normalized(o.text())).distinct().count() == 4;
    }
    private static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
    }
}
