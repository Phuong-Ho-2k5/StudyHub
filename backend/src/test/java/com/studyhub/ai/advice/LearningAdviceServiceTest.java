package com.studyhub.ai.advice;

import static com.studyhub.ai.advice.LearningAdviceModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.studyhub.ai.CitationMapper;
import com.studyhub.ai.RagAnswerClient.Evidence;
import com.studyhub.search.CourseSearchService;
import com.studyhub.search.dto.CourseSearchRequest;
import com.studyhub.search.dto.CourseSearchResponse;
import com.studyhub.security.ResourceAccessService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LearningAdviceServiceTest {
    final LearningContextService context=mock(LearningContextService.class);
    final CourseSearchService search=mock(CourseSearchService.class);
    final ResourceAccessService access=mock(ResourceAccessService.class);
    final LearningAdviceClient ai=mock(LearningAdviceClient.class);
    final CitationMapper citations=mock(CitationMapper.class);
    final LearningAdviceService service=new LearningAdviceService(context,search,access,ai,citations);
    final LearningFact fact=new LearningFact("concept:1","CONCEPT",Map.of("confidence",20));
    final LearningSnapshot snapshot=new LearningSnapshot(Instant.now(),true,List.of(fact),List.of(),"Atomicity");

    void ready() {
        when(context.build(1L,2L)).thenReturn(snapshot);
        when(search.search(eq(1L),eq(2L),any())).thenReturn(new CourseSearchResponse(1L,"Help",List.of()));
    }
    AdviceGenerationResult result(String status,List<String> ids,List<Evidence> evidence) {
        return new AdviceGenerationResult(status,"Review your weak concepts.",ids,evidence,"gpt-4.1-mini",PROMPT_VERSION);
    }

    @Test void coldStartSkipsAllHttpAndLearningOnlyStillCallsAiWithoutDocuments() {
        when(context.build(1L,2L)).thenReturn(new LearningSnapshot(Instant.now(),false,List.of(),List.of(),""));
        assertEquals("INSUFFICIENT_CONTEXT",service.advise(1L,2L,new AdviceRequest("Help",5)).status());
        verifyNoInteractions(search,ai,citations);
        ready(); when(ai.generate(anyString(),anyList(),anyList())).thenReturn(result("LEARNING_DATA_ONLY",List.of(fact.id()),List.of()));
        var response=service.advise(1L,2L,new AdviceRequest("Help",5));
        assertEquals("LEARNING_DATA_ONLY",response.status());assertEquals(List.of(fact),response.learningEvidence());
        assertTrue(response.limitations().contains("NO_RETRIEVED_DOCUMENTS"));
        verify(search).search(eq(1L),eq(2L),argThat(q -> q.getQuery().equals("Help\nAtomicity")));
        verifyNoInteractions(citations);
    }

    @Test void rejectsUnknownFactAndStateMismatch() {
        ready();
        for(var bad:List.of(result("LEARNING_DATA_ONLY",List.of("concept:999"),List.of()),
            result("LEARNING_DATA_ONLY",List.of(fact.id(),fact.id()),List.of()),
            result("ANSWERED",List.of(fact.id()),List.of()),
            result("INSUFFICIENT_CONTEXT",List.of(fact.id()),List.of()))) {
            when(ai.generate(anyString(),anyList(),anyList())).thenReturn(bad);
            assertEquals(502,assertThrows(ResponseStatusException.class,()->service.advise(1L,2L,new AdviceRequest("Help",5))).getStatusCode().value());
        }
    }

    @Test void rechecksOwnershipAfterGenerationAndPreservesDependencyFailures() {
        ready();when(ai.generate(anyString(),anyList(),anyList())).thenAnswer(call->{
            when(access.requireCourse(1L,2L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
            return result("LEARNING_DATA_ONLY",List.of(fact.id()),List.of());
        });
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.advise(1L,2L,new AdviceRequest("Help",5))).getStatusCode().value());
        reset(access);
        when(ai.generate(anyString(),anyList(),anyList())).thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT));
        assertEquals(504,assertThrows(ResponseStatusException.class,()->service.advise(1L,2L,new AdviceRequest("Help",5))).getStatusCode().value());
    }

    @Test void citationChangesReturnConflictAndInsufficientAnswerIsCanonical() {
        ready();
        when(ai.generate(anyString(),anyList(),anyList())).thenReturn(result("ANSWERED",List.of(fact.id()),List.of(new Evidence(1L,"Atomicity"))));
        when(citations.map(eq(1L),eq(2L),anyList(),anyList())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.advise(1L,2L,new AdviceRequest("Help",5))).getStatusCode().value());
        when(ai.generate(anyString(),anyList(),anyList())).thenReturn(result("INSUFFICIENT_CONTEXT",List.of(),List.of()));
        assertEquals(INSUFFICIENT_ANSWER,service.advise(1L,2L,new AdviceRequest("Help",5)).answer());
    }
}
