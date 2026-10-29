package com.studyhub.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DocumentSourceResponse(Long courseId,Long documentId,String documentTitle,Long chunkId,
        int chunkIndex,String content,@Schema(nullable=true) Integer pageStart,
        @Schema(nullable=true) Integer pageEnd,@Schema(nullable=true) String fileUrl) {}
