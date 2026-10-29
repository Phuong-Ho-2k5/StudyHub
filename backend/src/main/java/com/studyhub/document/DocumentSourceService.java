package com.studyhub.document;

import com.studyhub.document.dto.DocumentSourceResponse;
import com.studyhub.security.ResourceAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentSourceService {
    private final ResourceAccessService access;
    private final DocumentSourceRepository sources;
    public DocumentSourceService(ResourceAccessService access,DocumentSourceRepository sources) {
        this.access=access;this.sources=sources;
    }
    @Transactional(readOnly=true)
    public DocumentSourceResponse getSource(Long courseId,Long documentId,Long chunkId,Long userId) {
        access.requireCourse(courseId,userId);
        var document=access.requireDocument(documentId,userId);
        if(!document.getCourse().getId().equals(courseId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Document not found");
        var source=sources.findPublished(courseId,documentId,chunkId,userId).orElseThrow(()->
            new ResponseStatusException(HttpStatus.CONFLICT,"Citation source is no longer published. Ask the question again."));
        return new DocumentSourceResponse(source.courseId(),source.documentId(),source.documentTitle(),source.chunkId(),
            source.chunkIndex(),source.content(),source.pageStart(),source.pageEnd(),source.fileUrl());
    }
}
