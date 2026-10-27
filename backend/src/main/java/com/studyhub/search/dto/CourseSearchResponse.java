package com.studyhub.search.dto;

import java.util.List;

public record CourseSearchResponse(Long courseId,String query,List<SearchHitResponse> items) {
    public record SearchHitResponse(Long chunkId,Long documentId,String documentTitle,int chunkIndex,
            String content,Integer pageStart,Integer pageEnd,double score) {}
}
