package com.studyhub.notification;

import com.studyhub.quiz.QuizSubmittedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class QuizSubmittedListener {
    private final NotificationService service;

    public QuizSubmittedListener(NotificationService service) { this.service = service; }

    @EventListener
    public void onQuizSubmitted(QuizSubmittedEvent event) {
        service.createQuizSubmittedNotification(event.userId(), event.quizId(), event.questionPercentage());
    }
}
