package com.studyhub.ai.concept;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.studyhub.ai.dto.CourseCitationResponse;
import com.studyhub.concept.dto.ConceptResponse;
import java.time.Instant;
import java.util.List;

public final class ConceptSuggestionModels {
    private ConceptSuggestionModels() {}
    public static final String PROMPT_VERSION="document-concepts-v1";
    public record Context(Long chunkId, Long documentId, String documentTitle, int chunkIndex,
        String content, Integer pageStart, Integer pageEnd, @JsonIgnore boolean managedFile) {}
    public record SourceWindow(Long courseId, Long documentId, String publishedIngestionId,
        int requestedStartChunkIndex, List<Context> contexts, Integer nextChunkIndex) {}
    public record Evidence(Long chunkId, String quote) {}
    public record GeneratedSuggestion(String name, String description, List<Evidence> evidence) {}
    public record ExtractionResult(String model, String promptVersion, List<GeneratedSuggestion> suggestions) {}
    public record ValidatedSuggestion(String name, String description, List<CourseCitationResponse> citations) {}
    public record ValidatedExtraction(String model, String promptVersion, List<ValidatedSuggestion> suggestions) {}
    public record Coverage(int requestedStartChunkIndex, List<Long> includedChunkIds, Integer nextChunkIndex) {}
    public record DraftItem(Long id, String name, String description, String state, Long conceptId,
        List<CourseCitationResponse> citations) {}
    public record DraftBatch(String id, Long courseId, Long documentId, String publishedIngestionId,
        String model, String promptVersion, Instant createdAt, boolean sourceCurrent, Coverage coverage,
        List<DraftItem> suggestions) {}
    public record ConfirmationResponse(Long suggestionId, ConceptResponse concept) {}
    public record ConfirmationResult(boolean created, ConfirmationResponse response) {}

    public static boolean whitespace(int c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c==0x85;
    }
    public static String normalized(String value, int maximum) {
        if(value==null) throw new IllegalArgumentException("Text is required");
        int start=0,end=value.length();
        while(start<end && whitespace(value.codePointAt(start))) start+=Character.charCount(value.codePointAt(start));
        while(end>start && whitespace(value.codePointBefore(end))) end-=Character.charCount(value.codePointBefore(end));
        String result=value.substring(start,end);
        if(result.isEmpty() || result.codePointCount(0,result.length())>maximum)
            throw new IllegalArgumentException("Text is blank or exceeds its limit");
        return result;
    }
}
