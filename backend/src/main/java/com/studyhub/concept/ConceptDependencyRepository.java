package com.studyhub.concept;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConceptDependencyRepository extends JpaRepository<ConceptDependency, Long> {
    boolean existsByDependentConcept_IdAndPrerequisiteConcept_Id(Long conceptId, Long prerequisiteId);
    List<ConceptDependency> findAllByDependentConcept_Id(Long conceptId);
    Optional<ConceptDependency> findByDependentConcept_IdAndPrerequisiteConcept_Id(Long conceptId, Long prerequisiteId);
}
