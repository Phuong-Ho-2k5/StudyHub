package com.studyhub.quiz;

import static org.junit.jupiter.api.Assertions.*;
import com.studyhub.question.QuestionType;
import com.studyhub.question.dto.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AiQuizQuestionValidatorTest {
    @Test void unicodeEquivalentOrBlankOptionsAreRejected() {
        for(String option:List.of("\u00a0A\u00a0","\u00a0")) {
            var q=new QuestionRequest("Question",QuestionType.MULTIPLE_CHOICE,null,List.of(
                    new AnswerOptionRequest("A",true),new AnswerOptionRequest(option,false),
                    new AnswerOptionRequest("C",false),new AnswerOptionRequest("D",false)),null);
            assertEquals(400,assertThrows(ResponseStatusException.class,()->AiQuizQuestionValidator.validate(q)).getStatusCode().value());
        }
    }
}
