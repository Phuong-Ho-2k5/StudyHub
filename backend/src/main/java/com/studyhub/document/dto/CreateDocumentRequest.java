package com.studyhub.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 255) String fileName,
    @NotBlank @Size(max = 50) String fileType,
    @NotBlank @Size(max = 500) String storagePath
) {}