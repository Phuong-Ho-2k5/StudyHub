package com.studyhub.ai.job;

import com.studyhub.ai.job.AIJobModels.AIJobResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AIJobResponse(UUID id, AIJobType type, AIJobStatus status, Long courseId, Long documentId,
    Instant createdAt, Instant updatedAt, Instant startedAt, Instant finishedAt,
    AIJobResult result, AIJobErrorCode errorCode) {
    static AIJobResponse from(AIJobModels.Snapshot job) {
        return new AIJobResponse(job.id(),job.type(),job.status(),job.courseId(),job.documentId(),
            job.createdAt(),job.updatedAt(),job.startedAt(),job.finishedAt(),job.result(),job.errorCode());
    }
}
