package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.studyhub.document.DocumentSourceRepository;
import com.studyhub.document.DocumentSourceRepository.PublishedSource;
import com.studyhub.search.dto.CourseSearchResponse.SearchHitResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CitationMapperTest {
    final DocumentSourceRepository repository = mock(DocumentSourceRepository.class);
    final CitationMapper mapper = new CitationMapper(repository);
    final SearchHitResponse hit = new SearchHitResponse(1L,2L,"Old title",0,"Atomicity 😀",null,null,.8);
    final List<RagAnswerClient.Evidence> evidence = List.of(new RagAnswerClient.Evidence(1L,"Atomicity 😀"));
    PublishedSource source(String content) { return new PublishedSource(7L,2L,"Current title",1L,0,content,null,null,true); }
    @Test void mapsOnlyCitedSourcesUsingCurrentBackendMetadata() {
        when(repository.findPublishedByIds(7L,9L,List.of(1L))).thenReturn(List.of(source(hit.content())));
        var result=mapper.map(7L,9L,List.of(hit),evidence);
        assertEquals(1,result.size()); var citation=result.getFirst();
        assertEquals("Current title",citation.documentTitle()); assertNull(citation.pageStart());
        assertEquals("/api/courses/7/documents/2/chunks/1/source",citation.sourceUrl());
        assertEquals("/api/documents/2/file",citation.fileUrl());
    }
    @Test void rejectsRemovedOrChangedPublishedSource() {
        when(repository.findPublishedByIds(anyLong(),anyLong(),anyList())).thenReturn(List.of());
        assertStatus(409,()->mapper.map(7L,9L,List.of(hit),evidence));
        when(repository.findPublishedByIds(anyLong(),anyLong(),anyList())).thenReturn(List.of(source("New")));
        assertStatus(409,()->mapper.map(7L,9L,List.of(hit),evidence));
    }
    @Test void rejectsFabricationDuplicatesAndQuoteMismatchBeforeDatabase() {
        for(var bad:List.of(List.of(new RagAnswerClient.Evidence(3L,"Atomicity")),
                List.of(new RagAnswerClient.Evidence(1L,"invented")),List.of(evidence.getFirst(),evidence.getFirst())))
            assertStatus(502,()->mapper.map(7L,9L,List.of(hit),bad));
        verifyNoInteractions(repository);
    }
    @Test void metadataOnlyHasNoFileAndEvidenceOrderIsPreserved() {
        when(repository.findPublishedByIds(anyLong(),anyLong(),anyList())).thenReturn(List.of(
            new PublishedSource(7L,2L,"Metadata",1L,0,hit.content(),null,null,false)));
        assertNull(mapper.map(7L,9L,List.of(hit),evidence).getFirst().fileUrl());
    }
    @Test void unicodeWhitespaceOnlyEvidenceIsRejectedBeforeDatabase() {
        for(String quote:List.of("\u00a0","\u202f","\u0085")) {
            var blankHit=new SearchHitResponse(1L,2L,"DB",0,quote,null,null,.8);
            assertStatus(502,()->mapper.map(7L,9L,List.of(blankHit),List.of(new RagAnswerClient.Evidence(1L,quote))));
        }
        verifyNoInteractions(repository);
    }
    void assertStatus(int status,org.junit.jupiter.api.function.Executable call) {
        assertEquals(status,assertThrows(ResponseStatusException.class,call).getStatusCode().value());
    }
}
