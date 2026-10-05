package com.studyhub.question;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findAllByQuizIdOrderByIdAsc(Long quizId);
    Optional<Question> findByIdAndQuizCourseWorkspaceOwnerId(Long id, Long ownerId);
}
