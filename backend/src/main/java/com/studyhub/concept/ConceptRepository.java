package com.studyhub.concept;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConceptRepository extends JpaRepository<Concept, Long> {
    List<Concept> findAllByCourseId(Long courseId);
    Optional<Concept> findByIdAndCourseWorkspaceOwnerId(Long conceptId, Long ownerId);
}