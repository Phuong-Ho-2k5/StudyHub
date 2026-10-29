package com.studyhub.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CourseCitationResponse(Long chunkId,Long documentId,String documentTitle,int chunkIndex,
        @Schema(nullable=true) Integer pageStart,@Schema(nullable=true) Integer pageEnd,
        String quote,String sourceUrl,@Schema(nullable=true) String fileUrl) {}
