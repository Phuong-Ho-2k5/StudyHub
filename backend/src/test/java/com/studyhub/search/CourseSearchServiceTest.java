package com.studyhub.search;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.studyhub.document.DocumentEmbeddingClient;
import com.studyhub.security.ResourceAccessService;
import com.studyhub.search.dto.CourseSearchRequest;

class CourseSearchServiceTest {
    ResourceAccessService access=mock(ResourceAccessService.class);
    DocumentEmbeddingClient ai=mock(DocumentEmbeddingClient.class);
    CourseSearchRepository repository=mock(CourseSearchRepository.class);
    CourseSearchService service=new CourseSearchService(access,ai,repository);

    CourseSearchRequest request(){var input=new CourseSearchRequest();input.setQuery("  When to use transactions?  ");return input;}

    @Test void authorizesBeforeEmbeddingAndSearchesWithReturnedProfile() {
        var vector=List.of(1.,0.,0.);
        when(ai.embedQuery("When to use transactions?")).thenReturn(new DocumentEmbeddingClient.EmbeddingBatch("model@rev",3,List.of(vector)));
        when(repository.search(7L,9L,vector,"model@rev",3,5)).thenReturn(List.of(
                new CourseSearchRepository.SearchHit(1L,2L,"Database",0,"Transactions",1,2,1.)));
        var result=service.search(7L,9L,request());
        assertEquals("When to use transactions?",result.query());assertEquals(7L,result.courseId());
        assertEquals(1,result.items().size());assertEquals(1.,result.items().getFirst().score());
        var order=inOrder(access,ai,repository);
        order.verify(access).requireCourse(7L,9L);
        order.verify(ai).embedQuery("When to use transactions?");
        order.verify(repository).search(7L,9L,vector,"model@rev",3,5);
    }

    @Test void wrongOwnerDoesNotCallAiOrReadChunks() {
        when(access.requireCourse(7L,9L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.search(7L,9L,request())).getStatusCode().value());
        verifyNoInteractions(ai,repository);
    }

    @Test void aiFailureIsNotConvertedToEmptySuccess() {
        when(ai.embedQuery(anyString())).thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT));
        assertEquals(504,assertThrows(ResponseStatusException.class,()->service.search(7L,9L,request())).getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test void missingPublishedChunksReturnEmptyResults() {
        var vector=List.of(1.,0.,0.);
        when(ai.embedQuery(anyString())).thenReturn(new DocumentEmbeddingClient.EmbeddingBatch("model@rev",3,List.of(vector)));
        when(repository.search(anyLong(),anyLong(),anyList(),anyString(),anyInt(),anyInt())).thenReturn(List.of());
        assertTrue(service.search(7L,9L,request()).items().isEmpty());
    }
}
