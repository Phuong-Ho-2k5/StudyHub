package com.studyhub.document.dto;

import com.studyhub.document.DocumentStatus;

public record DocumentResponse(
    Long id,
    Long courseId,
    String title,
    String fileName,
    String fileType,
    String storagePath,
    DocumentStatus status,
    boolean managedFile,
    com.studyhub.document.DocumentIngestionStatus ingestionStatus,
    String publishedIngestionId,
    String ingestionError
) {}
