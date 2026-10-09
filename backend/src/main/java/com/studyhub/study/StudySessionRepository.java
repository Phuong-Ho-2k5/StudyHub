package com.studyhub.study;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudySession s where s.id = :id and s.user.id = :userId")
    Optional<StudySession> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
