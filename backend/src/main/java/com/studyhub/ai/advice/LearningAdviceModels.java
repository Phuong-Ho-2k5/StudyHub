package com.studyhub.ai.advice;

import com.studyhub.ai.RagAnswerClient.Evidence;
import com.studyhub.ai.dto.CourseCitationResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class LearningAdviceModels {
    private LearningAdviceModels() {}
    public static final String PROMPT_VERSION = "learning-advice-v1";
    public static final String INSUFFICIENT_ANSWER = "Chưa đủ dữ liệu học tập để đưa ra lời khuyên cá nhân hóa cho khóa học này.";
    public record LearningFact(String id, String type, Map<String,Object> data) {}
    public record LearningSnapshot(Instant asOf, boolean hasLearningData, List<LearningFact> facts,
                                   List<String> limitations, String retrievalHint) {}
    public record AdviceGenerationResult(String status, String answer, List<String> usedLearningEvidenceIds,
                                         List<Evidence> evidence, String model, String promptVersion) {}
    public record AdviceResponse(Long courseId, String question, String status, String answer, String asOf,
                                 String model, String promptVersion, List<LearningFact> learningEvidence,
                                 List<CourseCitationResponse> citations, List<String> limitations) {}
}
