package com.studyhub.study;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {
    @Query("""
            select count(s.id) as completedStudySessions,
                   coalesce(sum(s.duration), 0) as totalStudySeconds
            from StudySession s
            where s.user.id = :userId and s.endTime is not null
            """)
    StudyStatistics summarizeCompletedByUserId(@Param("userId") Long userId);

    interface StudyStatistics {
        long getCompletedStudySessions();
        long getTotalStudySeconds();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudySession s where s.id = :id and s.user.id = :userId")
    Optional<StudySession> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
