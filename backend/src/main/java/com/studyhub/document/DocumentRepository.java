package com.studyhub.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {
    List<Document> findAllByCourseId(Long courseId);
    Optional<Document> findByIdAndCourseWorkspaceOwnerId(Long documentId, Long ownerId);
    Page<Document> findAllByCourseId(Long courseId, Pageable pageable);
}
