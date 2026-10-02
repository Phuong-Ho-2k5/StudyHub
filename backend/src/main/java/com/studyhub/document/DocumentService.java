package com.studyhub.document;

import java.util.List;
import org.springframework.data.domain.Page;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyhub.security.ResourceAccessService;
import com.studyhub.course.Course;
import com.studyhub.document.dto.UpdateDocumentRequest;
import com.studyhub.document.dto.CreateDocumentRequest;
import com.studyhub.document.dto.DocumentResponse;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.Locale;
import jakarta.persistence.criteria.Predicate;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final ResourceAccessService resourceAccessService;

    public DocumentService(DocumentRepository documentRepository, ResourceAccessService resourceAccessService) {
        this.documentRepository = documentRepository;
        this.resourceAccessService = resourceAccessService;
    }

    @Transactional
    public DocumentResponse createDocument(Long courseId, CreateDocumentRequest request, Long currentUserId) {
        Course course = resourceAccessService.requireCourse(courseId, currentUserId);
        Document document = new Document(request.title(), request.fileName(), request.fileType(),
                request.storagePath(), DocumentStatus.READY, course);
        return toResponse(documentRepository.save(document));
    }

    @Transactional
    public DocumentResponse updateDocument(Long documentId, UpdateDocumentRequest request, Long currentUserId) {
        Document document = resourceAccessService.requireDocument(documentId, currentUserId);
        document.setTitle(request.title());
        document.setFileName(request.fileName());
        document.setFileType(request.fileType());
        document.setStoragePath(request.storagePath());
        return toResponse(documentRepository.save(document));
    }

    @Transactional
    public void deleteDocument(Long documentId, Long currentUserId) {
        Document document = resourceAccessService.requireDocument(documentId, currentUserId);
        documentRepository.delete(document);
    }

    @Transactional (readOnly = true)
    public DocumentResponse getDocument(Long documentId, Long currentUserId) {
        Document document = resourceAccessService.requireDocument(documentId, currentUserId);
        return toResponse(document);
    } 

    @Transactional (readOnly = true)
    public Page<DocumentResponse> getAllDocument(Long userId, Long workspaceId, Long courseId, DocumentStatus status, String q, Pageable pageable) {
        Specification<Document> filter = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(root.get("course").get("workspace").get("owner").get("id"), userId));

            if (courseId != null){
                conditions.add(cb.equal(root.get("course").get("id"), courseId));
            }

            if (workspaceId != null) {
                conditions.add(cb.equal(root.get("course").get("workspace").get("id"), workspaceId));
            }

            if (status != null) {
                conditions.add(cb.equal(root.get("status"), status));
            }

            if (q != null && !q.isBlank()) {
                conditions.add(cb.like(cb.lower(root.get("title")),"%" + q.trim().toLowerCase(Locale.ROOT) + "%"));
            }

            return cb.and(conditions.toArray(Predicate[]::new));
        };
        return documentRepository.findAll(filter, pageable).map(this::toResponse);
    }

    private DocumentResponse toResponse(Document document) {
        return new DocumentResponse(document.getId(), document.getCourse().getId(), document.getTitle(), document.getFileName(),
                document.getFileType(), document.getStoragePath(), document.getStatus());
    }
}
