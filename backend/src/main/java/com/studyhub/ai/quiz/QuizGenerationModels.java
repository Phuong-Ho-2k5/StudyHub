package com.studyhub.ai.quiz;

import com.studyhub.ai.concept.ConceptSuggestionModels.Context;
import com.studyhub.ai.dto.CourseCitationResponse;
import com.studyhub.question.dto.QuestionResponse;
import com.studyhub.quiz.dto.QuizResponse;
import java.util.List;

public final class QuizGenerationModels {
    private QuizGenerationModels() {}
    public static final String PROMPT_VERSION="quiz-generation-v1";
    public record DocumentCoverage(Long documentId,String ingestionId,List<Long> includedChunkIds) {}
    public record SourceWindow(Long courseId,List<DocumentCoverage> coverage,List<Context> contexts) {}
    public record Option(String text,boolean correct) {}
    public record Evidence(Long chunkId,String quote) {}
    public record GeneratedQuestion(String text,List<Option> options,List<Evidence> evidence) {}
    public record GeneratedResult(String model,String promptVersion,List<GeneratedQuestion> questions) {}
    public record ValidatedQuestion(String text,List<Option> options,List<CourseCitationResponse> citations) {}
    public record ValidatedResult(String model,String promptVersion,List<ValidatedQuestion> questions) {}
    public record QuestionEvidence(Long questionId,List<CourseCitationResponse> citations) {}
    public record GenerationMetadata(String id,String model,String promptVersion,List<DocumentCoverage> coverage,
        List<QuestionEvidence> evidence,boolean sourceCurrent) {}
    public record GenerationResponse(QuizResponse quiz,List<QuestionResponse> questions,GenerationMetadata generation) {}
    public record GenerationResult(boolean created,GenerationResponse response) {}
}
