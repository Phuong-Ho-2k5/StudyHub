package com.studyhub.document;

import java.util.List;
import org.springframework.data.domain.Page;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.studyhub.user.User;
import com.studyhub.user.UserRepository;
import com.studyhub.workspace.Workspace;
import com.studyhub.workspace.WorkspaceRepository;
import com.studyhub.course.Course;
import com.studyhub.course.CourseRepository;
import com.studyhub.document.Document;
import com.studyhub.document.DocumentRepository;
import com.studyhub.document.dto.UpdateDocumentRequest;
import com.studyhub.document.dto.CreateDocumentRequest;
import com.studyhub.document.dto.DocumentResponse;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import jakarta.persistence.criteria.Predicate;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final CourseRepository courseRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;

    public DocumentService(DocumentRepository documentRepository, CourseRepository courseRepository, WorkspaceRepository workspaceRepository, UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.courseRepository = courseRepository;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public DocumentResponse createDocument(Long courseId, CreateDocumentRequest request, Long currentUserId) {
        Course course = courseRepository.findByIdAndWorkspaceOwnerId(courseId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        Document document = new Document(request.title(), request.fileName(), request.fileType(),
                request.storagePath(), DocumentStatus.READY, course);
        return toResponse(documentRepository.save(document));
    }

    @Transactional
    public DocumentResponse updateDocument(Long documentId, UpdateDocumentRequest request, Long currentUserId) {
        Document document = documentRepository.findByIdAndCourseWorkspaceOwnerId(documentId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        document.setTitle(request.title());
        document.setFileName(request.fileName());
        document.setFileType(request.fileType());
        document.setStoragePath(request.storagePath());
        return toResponse(documentRepository.save(document));
    }

    @Transactional
    public void deleteDocument(Long documentId, Long currentUserId) {
        Document document = documentRepository.findByIdAndCourseWorkspaceOwnerId(documentId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        documentRepository.delete(document);
    }

    @Transactional (readOnly = true)
    public DocumentResponse getDocument(Long documentId, Long currentUserId) {
        Document document = documentRepository.findByIdAndCourseWorkspaceOwnerId(documentId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
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
