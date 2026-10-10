package com.studyhub.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    @Query("""
            select count(a.id) as quizAttemptCount,
                   coalesce(avg(a.questionPercentage), 0.0) as averageQuizScore
            from QuizAttempt a
            where a.user.id = :userId
            """)
    QuizStatistics summarizeByUserId(@Param("userId") Long userId);

    interface QuizStatistics {
        long getQuizAttemptCount();
        double getAverageQuizScore();
    }
}
