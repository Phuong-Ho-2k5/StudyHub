package com.studyhub.quiz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    @EntityGraph(attributePaths = "quiz")
    Page<QuizAttempt> findByUserId(Long userId, Pageable pageable);

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
