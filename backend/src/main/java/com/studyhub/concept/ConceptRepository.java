package com.studyhub.concept;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface ConceptRepository extends JpaRepository<Concept, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Concept c where c.id = :conceptId and c.course.workspace.owner.id = :ownerId")
    Optional<Concept> findForUpdateInScope(Long conceptId, Long ownerId);
    List<Concept> findAllByCourseId(Long courseId);
    Optional<Concept> findByIdAndCourseWorkspaceOwnerId(Long conceptId, Long ownerId);
    List<Concept> findAllByIdInAndCourseWorkspaceOwnerId(Collection<Long> ids, Long ownerId);
    List<Concept> findAllByCourseIdAndConfidenceLessThan(Long courseId, Integer confidence);
}
