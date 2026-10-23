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
import com.studyhub.document.storage.DocumentStorage;
import com.studyhub.document.storage.DocumentFileLifecycle;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final ResourceAccessService resourceAccessService;
    private final DocumentStorage storage;
    private final DocumentFileLifecycle files;

    public DocumentService(DocumentRepository documentRepository, ResourceAccessService resourceAccessService, DocumentStorage storage, DocumentFileLifecycle files) {
        this.documentRepository = documentRepository;
        this.resourceAccessService = resourceAccessService;
        this.storage = storage;
        this.files = files;
    }

    @Transactional
    public DocumentResponse upload(Long courseId, String title, MultipartFile file, Long userId) {
        Course course = resourceAccessService.requireCourse(courseId, userId);
        if (title == null || title.isBlank() || title.length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề phải có từ 1 đến 200 ký tự.");
        var stored = storage.store(file);
        files.uploaded(stored.key());
        Document document = new Document(title.trim(), stored.fileName(), "application/pdf", stored.key(), DocumentStatus.READY, course);
        document.setManagedFile(true);
        return toResponse(documentRepository.saveAndFlush(document));
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
        if (document.isManagedFile() && (!java.util.Objects.equals(document.getStoragePath(), request.storagePath())
                || !java.util.Objects.equals(document.getFileName(), request.fileName())
                || !java.util.Objects.equals(document.getFileType(), request.fileType())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể đổi tiêu đề của tài liệu đã tải lên.");
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
        files.deleteDocument(document);
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
                document.getFileType(), document.getStoragePath(), document.getStatus(), document.isManagedFile());
    }
}
