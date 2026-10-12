package com.studyhub.question;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    @EntityGraph(attributePaths = "options")
    List<Question> findAllByQuizIdOrderByIdAsc(Long quizId);
    Optional<Question> findByIdAndQuizCourseWorkspaceOwnerId(Long id, Long ownerId);
}
