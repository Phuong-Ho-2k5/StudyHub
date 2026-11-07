package com.studyhub.concept.recommendation;

import com.studyhub.ai.advice.LearningAdviceModels.LearningFact;
import java.util.List;

public final class NextConceptModels {
    public static final int READINESS_THRESHOLD = 80;
    public static final String ALGORITHM_VERSION = "next-concepts-v1";

    private NextConceptModels() {}

    public record ConceptNode(long id, String name, int confidence) {}
    /** A null prerequisiteId marks a prerequisite outside the requested Course. */
    public record PrerequisiteEdge(long dependentId, Long prerequisiteId) {}
    public record GraphSnapshot(List<ConceptNode> concepts, List<PrerequisiteEdge> edges) {}
    public record RankedConcept(ConceptNode concept, int unlocksCount) {}
    public record RankingResult(String status, List<RankedConcept> concepts, List<String> limitations) {}
    public record NextConceptRecommendation(int rank, long conceptId, String name, int confidence,
        String action, List<String> reasonCodes, int unlocksCount, List<String> learningEvidenceIds) {}
    public record NextConceptResponse(long courseId, String asOf, String algorithmVersion, String status,
        List<NextConceptRecommendation> recommendations, List<LearningFact> learningEvidence,
        List<String> limitations) {}
}
