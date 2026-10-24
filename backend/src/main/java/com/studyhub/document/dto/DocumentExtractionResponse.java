package com.studyhub.document.dto;

import java.util.List;

public record DocumentExtractionResponse(Long documentId, String source, int pageCount,
        List<ExtractedPage> pages, List<String> warnings) {
    public record ExtractedPage(int pageNumber, String text, List<ContentBlock> blocks) {}
    public record BoundingBox(double l, double t, double r, double b, String coordOrigin) {}
    public record ContentBlock(String id, String type, String text, BoundingBox bbox,
            String description, String descriptionMethod) {}
}
