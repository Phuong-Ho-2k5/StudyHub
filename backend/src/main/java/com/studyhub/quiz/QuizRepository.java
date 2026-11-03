package com.studyhub.quiz;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select q from Quiz q where q.id = :id and q.course.workspace.owner.id = :ownerId")
    Optional<Quiz> findForUpdateByIdAndCourseWorkspaceOwnerId(Long id, Long ownerId);
    List<Quiz> findAllByCourseIdOrderByIdAsc(Long courseId);
    Optional<Quiz> findByIdAndCourseWorkspaceOwnerId(Long id, Long ownerId);
}
