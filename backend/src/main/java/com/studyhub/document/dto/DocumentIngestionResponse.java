package com.studyhub.document.dto;
import java.util.List;
public record DocumentIngestionResponse(Long documentId, int chunkCount, String embeddingModel,
        int embeddingDimensions, List<String> warnings) {}
