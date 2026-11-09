package com.studyhub.ai.job;

import java.time.Instant;
import java.util.UUID;

public final class AIJobModels {
    private AIJobModels() {}
    public record CreateAIJob(AIJobType type, Long courseId, Long documentId) {}
    public record AIJobClaim(UUID jobId, UUID token) {
        @Override public String toString() {return "AIJobClaim[jobId="+jobId+"]";}
    }
    public record AIJobResult(AIJobResultType type, String id) {
        public AIJobResult {
            if(type==null || id==null || id.isBlank() || id.length()>100)
                throw new IllegalArgumentException("A result requires a type and a nonblank ID of at most 100 characters");
        }
    }
    record Snapshot(UUID id, long requestedBy, long courseId, Long documentId, AIJobType type,
        AIJobStatus status, String claimToken, AIJobResult result, AIJobErrorCode errorCode,
        Instant createdAt, Instant updatedAt, Instant startedAt, Instant finishedAt) {}
}
