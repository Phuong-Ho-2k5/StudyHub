package com.studyhub.ai;

import com.studyhub.ai.dto.CourseCitationResponse;
import com.studyhub.document.DocumentSourceRepository;
import com.studyhub.search.dto.CourseSearchResponse.SearchHitResponse;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CitationMapper {
    private final DocumentSourceRepository sources;
    public CitationMapper(DocumentSourceRepository sources) { this.sources=sources; }

    public List<CourseCitationResponse> map(Long courseId,Long userId,List<SearchHitResponse> retrieved,
            List<RagAnswerClient.Evidence> evidence) {
        var hits=retrieved.stream().collect(Collectors.toMap(SearchHitResponse::chunkId,Function.identity()));
        var seen=new HashSet<Long>();
        if(evidence==null || evidence.isEmpty() || evidence.size()>20) throw invalid();
        for(var item:evidence) {
            if(item==null || item.chunkId()==null || !item.hasText()
                    || item.quote().codePointCount(0,item.quote().length())>1000
                    || !seen.add(item.chunkId()) || !hits.containsKey(item.chunkId())
                    || !hits.get(item.chunkId()).content().contains(item.quote())) throw invalid();
        }
        var current=sources.findPublishedByIds(courseId,userId,evidence.stream().map(RagAnswerClient.Evidence::chunkId).toList())
            .stream().collect(Collectors.toMap(DocumentSourceRepository.PublishedSource::chunkId,Function.identity()));
        var result=new ArrayList<CourseCitationResponse>();
        for(var item:evidence) {
            var hit=hits.get(item.chunkId()); var source=current.get(item.chunkId());
            if(source==null || !Objects.equals(source.documentId(),hit.documentId())
                    || source.chunkIndex()!=hit.chunkIndex() || !Objects.equals(source.content(),hit.content())
                    || !Objects.equals(source.pageStart(),hit.pageStart()) || !Objects.equals(source.pageEnd(),hit.pageEnd()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Citation source changed. Ask the question again.");
            result.add(new CourseCitationResponse(source.chunkId(),source.documentId(),source.documentTitle(),source.chunkIndex(),
                source.pageStart(),source.pageEnd(),item.quote(),"/api/courses/"+courseId+"/documents/"+source.documentId()
                +"/chunks/"+source.chunkId()+"/source",source.fileUrl()));
        }
        return List.copyOf(result);
    }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI chat returned invalid evidence.");
    }
}
