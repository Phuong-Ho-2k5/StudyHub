package com.studyhub.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.studyhub.document.dto.CreateDocumentRequest;
import com.studyhub.document.dto.UpdateDocumentRequest;
import com.studyhub.document.dto.DocumentResponse;
import com.studyhub.security.CustomUserDetails;

import org.springframework.data.domain.Pageable;

import com.studyhub.document.DocumentService;
import java.util.List;
import org.springframework.data.domain.Page;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/courses/{courseId}/documents")
    public ResponseEntity<DocumentResponse> createDocument(
            @PathVariable Long courseId,
            @Valid @RequestBody CreateDocumentRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        DocumentResponse response = documentService.createDocument(courseId, request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/documents/{documentId}")
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long documentId,
            @Valid @RequestBody UpdateDocumentRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        DocumentResponse response = documentService.updateDocument(documentId, request, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long documentId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        documentService.deleteDocument(documentId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable Long documentId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        DocumentResponse response = documentService.getDocument(documentId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/documents")
    public ResponseEntity<Page<DocumentResponse>> getAllDocument(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Long workspaceId,
            Long courseId,
            DocumentStatus status,
            String q,
            Pageable pageable) {
        Page<DocumentResponse> response = documentService.getAllDocument(currentUser.getId(), workspaceId, courseId, status, q, pageable);
        return ResponseEntity.ok(response);
    }
}
