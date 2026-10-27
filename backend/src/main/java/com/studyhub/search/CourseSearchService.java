package com.studyhub.search;

import org.springframework.stereotype.Service;
import com.studyhub.document.DocumentEmbeddingClient;
import com.studyhub.security.ResourceAccessService;
import com.studyhub.search.dto.CourseSearchRequest;
import com.studyhub.search.dto.CourseSearchResponse;
import com.studyhub.search.dto.CourseSearchResponse.SearchHitResponse;

@Service
public class CourseSearchService {
    private final ResourceAccessService access;
    private final DocumentEmbeddingClient ai;
    private final CourseSearchRepository repository;
    public CourseSearchService(ResourceAccessService access,DocumentEmbeddingClient ai,CourseSearchRepository repository){
        this.access=access;this.ai=ai;this.repository=repository;
    }

    public CourseSearchResponse search(Long courseId,Long userId,CourseSearchRequest request) {
        access.requireCourse(courseId,userId);
        String query=request.getQuery().strip();
        // No database transaction is kept open while waiting for the model.
        var batch=ai.embedQuery(query);
        var items=repository.search(courseId,userId,batch.vectors().getFirst(),batch.model(),batch.dimensions(),request.getTopK())
                .stream().map(hit->new SearchHitResponse(hit.chunkId(),hit.documentId(),hit.documentTitle(),hit.chunkIndex(),
                        hit.content(),hit.pageStart(),hit.pageEnd(),hit.score())).toList();
        return new CourseSearchResponse(courseId,query,items);
    }
}
