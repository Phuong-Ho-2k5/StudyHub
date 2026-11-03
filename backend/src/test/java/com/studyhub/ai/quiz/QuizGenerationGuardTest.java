package com.studyhub.ai.quiz;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.studyhub.quiz.*;
import com.studyhub.quiz.dto.UpdateQuizRequest;
import com.studyhub.security.ResourceAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class QuizGenerationGuardTest {
    @Test void cannotPublishAiDraftThroughCrudButManualStillWorks() {
        var access=mock(ResourceAccessService.class);var service=new QuizService(mock(QuizRepository.class),access);
        var ai=Quiz.aiGenerated("AI",null);when(access.requireQuiz(1L,1L)).thenReturn(ai);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.update(1L,new UpdateQuizRequest("AI",QuizStatus.PUBLISHED),1L)).getStatusCode().value());
        assertEquals(QuizStatus.DRAFT,ai.getStatus());
        var manual=new Quiz("Manual",null);when(access.requireQuiz(2L,1L)).thenReturn(manual);
        // Response construction needs a Course; mutation itself remains allowed for MANUAL.
        var course=mock(com.studyhub.course.Course.class);
        manual=new Quiz("Manual",course);when(access.requireQuiz(2L,1L)).thenReturn(manual);
        assertEquals(QuizStatus.PUBLISHED,service.update(2L,new UpdateQuizRequest("Manual",QuizStatus.PUBLISHED),1L).status());
    }
}
