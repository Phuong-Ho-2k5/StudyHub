package com.studyhub.concept;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface ConceptDependencyRepository extends JpaRepository<ConceptDependency, Long> {
    boolean existsByDependentConcept_IdAndPrerequisiteConcept_Id(Long conceptId, Long prerequisiteId);
    @EntityGraph(attributePaths = "prerequisiteConcept")
    List<ConceptDependency> findAllByDependentConcept_Id(Long conceptId);
    Optional<ConceptDependency> findByDependentConcept_IdAndPrerequisiteConcept_Id(Long conceptId, Long prerequisiteId);
}
