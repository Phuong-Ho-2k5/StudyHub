package com.studyhub.user;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.studyhub.quiz.QuizAttemptRepository;
import com.studyhub.study.StudySessionRepository;
import com.studyhub.user.dto.UserStatisticsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserStatisticsService {
    private final StudySessionRepository sessions;
    private final QuizAttemptRepository attempts;

    public UserStatisticsService(StudySessionRepository sessions, QuizAttemptRepository attempts) {
        this.sessions = sessions;
        this.attempts = attempts;
    }

    @Transactional(readOnly = true)
    public UserStatisticsResponse getStatistics(Long currentUserId) {
        var study = sessions.summarizeCompletedByUserId(currentUserId);
        var quiz = attempts.summarizeByUserId(currentUserId);
        double average = BigDecimal.valueOf(quiz.getAverageQuizScore())
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
        return new UserStatisticsResponse(study.getCompletedStudySessions(), study.getTotalStudySeconds(),
                quiz.getQuizAttemptCount(), average);
    }
}
