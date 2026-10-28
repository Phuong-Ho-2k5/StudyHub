package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.studyhub.ai.dto.CourseChatRequest;
import com.studyhub.search.CourseSearchService;
import com.studyhub.search.dto.CourseSearchResponse;
import com.studyhub.search.dto.CourseSearchResponse.SearchHitResponse;
import com.studyhub.security.ResourceAccessService;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CourseChatServiceTest {
    final CourseSearchService search = mock(CourseSearchService.class);
    final ResourceAccessService access = mock(ResourceAccessService.class);
    final RagAnswerClient ai = mock(RagAnswerClient.class);
    final CourseChatService service = new CourseChatService(search, access, ai);

    CourseChatRequest request() { var r = new CourseChatRequest(); r.setQuestion("  Transaction?  "); return r; }
    List<SearchHitResponse> hits() { return List.of(new SearchHitResponse(1L, 2L, "Database", 0, "Atomicity", 1, 2, .8)); }

    @Test void retrievesOnceAndChecksOwnershipAfterGeneration() {
        when(search.search(eq(7L), eq(9L), any())).thenAnswer(invocation -> {
            var r = invocation.getArgument(2, com.studyhub.search.dto.CourseSearchRequest.class);
            assertEquals("Transaction?", r.getQuery()); assertEquals(5, r.getTopK());
            return new CourseSearchResponse(7L, r.getQuery(), hits());
        });
        when(ai.answer("Transaction?", hits())).thenReturn(new RagAnswerClient.RagAnswerResult("ANSWERED", "Atomicity", "model", "course-rag-v1"));
        var result = service.chat(7L, 9L, request());
        assertEquals("ANSWERED", result.status()); assertEquals("Atomicity", result.answer());
        assertEquals("Transaction?", result.question());
        var order = inOrder(search, ai, access);
        order.verify(search).search(eq(7L), eq(9L), any());
        order.verify(ai).answer("Transaction?", hits());
        order.verify(access).requireCourse(7L, 9L);
        verifyNoMoreInteractions(search);
    }

    @Test void emptyRetrievalDoesNotGenerateAndStillRechecksCourse() {
        when(search.search(eq(7L), eq(9L), any())).thenReturn(new CourseSearchResponse(7L, "Transaction?", List.of()));
        var result = service.chat(7L, 9L, request());
        assertEquals("INSUFFICIENT_CONTEXT", result.status()); assertNull(result.model());
        assertEquals(CourseChatService.INSUFFICIENT_ANSWER, result.answer());
        verifyNoInteractions(ai); verify(access).requireCourse(7L, 9L);
    }

    @Test void unauthorizedCourseNeverGenerates() {
        when(search.search(anyLong(), anyLong(), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.chat(7L, 9L, request())).getStatusCode().value());
        verifyNoInteractions(ai);
    }

    @Test void insufficientEvidenceUsesFixedAnswerAndPreservesModel() {
        when(search.search(anyLong(), anyLong(), any())).thenReturn(new CourseSearchResponse(7L, "Transaction?", hits()));
        when(ai.answer(anyString(), anyList())).thenReturn(new RagAnswerClient.RagAnswerResult("INSUFFICIENT_CONTEXT", "untrusted wording", "model", "course-rag-v1"));
        var result = service.chat(7L, 9L, request());
        assertEquals(CourseChatService.INSUFFICIENT_ANSWER, result.answer()); assertEquals("model", result.model());
    }

    @Test void deletionWhileGeneratingIsNotReturnedAsSuccess() {
        when(search.search(anyLong(), anyLong(), any())).thenReturn(new CourseSearchResponse(7L, "Transaction?", hits()));
        when(ai.answer(anyString(), anyList())).thenReturn(new RagAnswerClient.RagAnswerResult("ANSWERED", "Answer", "model", "course-rag-v1"));
        when(access.requireCourse(7L, 9L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.chat(7L, 9L, request())).getStatusCode().value());
    }

    @Test void generationFailureIsNotConvertedToInsufficientContext() {
        when(search.search(anyLong(), anyLong(), any())).thenReturn(new CourseSearchResponse(7L, "Transaction?", hits()));
        when(ai.answer(anyString(), anyList())).thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT));
        assertEquals(504, assertThrows(ResponseStatusException.class, () -> service.chat(7L, 9L, request())).getStatusCode().value());
    }

    @Test void publishDuringGenerationKeepsRetrievedSnapshotAndNextRequestSeesNewVersion() throws Exception {
        var version=new AtomicReference<>(hits());
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(search.search(anyLong(),anyLong(),any())).thenAnswer(i->new CourseSearchResponse(7L,"Transaction?",version.get()));
        when(ai.answer(anyString(),anyList())).thenAnswer(i->{
            var contexts=i.<List<SearchHitResponse>>getArgument(1);
            entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));
            return new RagAnswerClient.RagAnswerResult("ANSWERED",contexts.getFirst().content(),"model","course-rag-v1");
        });
        try(var pool=Executors.newSingleThreadExecutor()) {
            var pending=pool.submit(()->service.chat(7L,9L,request()));
            try {
                assertTrue(entered.await(10,TimeUnit.SECONDS));
                version.set(List.of(new SearchHitResponse(3L,2L,"Database",0,"New version",1,1,.8)));
            } finally { release.countDown(); }
            assertEquals("Atomicity",pending.get(10,TimeUnit.SECONDS).answer());
            assertEquals("New version",service.chat(7L,9L,request()).answer());
        }
    }

    @Test void deletionWhileGenerationIsBlockedRechecksOwnershipBeforeReturning() throws Exception {
        when(search.search(anyLong(),anyLong(),any())).thenReturn(new CourseSearchResponse(7L,"Transaction?",hits()));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(ai.answer(anyString(),anyList())).thenAnswer(i->{
            entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));
            return new RagAnswerClient.RagAnswerResult("ANSWERED","Atomicity","model","course-rag-v1");
        });
        try(var pool=Executors.newSingleThreadExecutor()) {
            var pending=pool.submit(()->service.chat(7L,9L,request()));
            try {
                assertTrue(entered.await(10,TimeUnit.SECONDS));
                when(access.requireCourse(7L,9L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
            } finally { release.countDown(); }
            var error=assertThrows(ExecutionException.class,()->pending.get(10,TimeUnit.SECONDS));
            assertEquals(404,((ResponseStatusException)error.getCause()).getStatusCode().value());
        }
    }
}
