package com.studyhub.quiz;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
    List<Quiz> findAllByCourseIdOrderByIdAsc(Long courseId);
    Optional<Quiz> findByIdAndCourseWorkspaceOwnerId(Long id, Long ownerId);
}
