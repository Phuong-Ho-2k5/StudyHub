package com.studyhub.document.dto;

import java.util.List;

public record DocumentExtractionResponse(Long documentId, String source, int pageCount,
        List<ExtractedPage> pages, List<String> warnings, List<Chunk> chunks) {
    public DocumentExtractionResponse(Long documentId, String source, int pageCount,
            List<ExtractedPage> pages, List<String> warnings) {
        this(documentId, source, pageCount, pages, warnings, null);
    }
    public record ExtractedPage(int pageNumber, String text, List<ContentBlock> blocks) {}
    public record BoundingBox(double l, double t, double r, double b, String coordOrigin) {}
    public record ContentBlock(String id, String type, String text, BoundingBox bbox,
            String description, String descriptionMethod) {}
    public record SourceReference(String blockId, int pageNumber, BoundingBox bbox) {}
    public record Chunk(Long documentId, String source, int chunkIndex, String content,
            String embeddingText, List<String> headings, int pageStart, int pageEnd,
            List<String> blockIds, List<SourceReference> sourceRefs, String overlapContent,
            List<SourceReference> overlapSourceRefs, int tokenCount, String tokenizerModel) {
        public Chunk withSource(String trustedSource) {
            return new Chunk(documentId, trustedSource, chunkIndex, content, embeddingText, headings,
                    pageStart, pageEnd, blockIds, sourceRefs, overlapContent, overlapSourceRefs,
                    tokenCount, tokenizerModel);
        }
    }
}
