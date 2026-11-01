package com.studyhub.ai;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ChatSessionRepository extends JpaRepository<ChatSession,Long> {
    Optional<ChatSession> findByIdAndCourseIdAndUserId(Long id, Long courseId, Long userId);
    Page<ChatSession> findByCourseIdAndUserId(Long courseId, Long userId, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChatSession s where s.id=:id and s.courseId=:courseId and s.userId=:userId")
    Optional<ChatSession> lockOwnedSession(@Param("id") Long id, @Param("courseId") Long courseId, @Param("userId") Long userId);
}
