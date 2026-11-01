package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.studyhub.document.DocumentSourceRepository;
import com.studyhub.search.CourseSearchService;
import com.studyhub.search.dto.CourseSearchResponse;
import com.studyhub.search.dto.CourseSearchResponse.SearchHitResponse;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class SessionChatApiTest extends ChatHistoryTestSupport {
    @MockitoBean CourseSearchService search;
    @MockitoBean RagAnswerClient ai;
    @MockitoBean DocumentSourceRepository sources;

    void answer(long course) {
        when(search.search(eq(course),anyLong(),any())).thenAnswer(i->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"HTTP work must not hold a transaction");
            return new CourseSearchResponse(course,"Q",List.of(new SearchHitResponse(999L,888L,"Tài liệu",0,"Nguyên tử",null,null,.9)));
        });
        when(ai.answer(anyString(),anyList())).thenAnswer(i->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new RagAnswerClient.RagAnswerResult("ANSWERED","Giải thích nguyên tử","test-model","course-rag-v2",List.of(new RagAnswerClient.Evidence(999L,"Nguyên tử")));
        });
        when(sources.findPublishedByIds(eq(course),anyLong(),anyList())).thenReturn(List.of(
            new DocumentSourceRepository.PublishedSource(course,888L,"Tài liệu",999L,0,"Nguyên tử",null,null,false)));
    }
    String chat(long course) { return "/api/courses/"+course+"/ai/chat"; }
    int count(long session) { return jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=?",Integer.class,session); }

    @Test void savesCompleteTurnsAndReadsSnapshotWithoutCallingAiAgain() throws Exception {
        var f=fixture(); long id=session(f); session(f); answer(f.course());
        var result=call("POST",chat(f.course()),f.token(),Map.of("question","  Q  ","sessionId",id),200);
        assertEquals(id,result.path("sessionId").asLong());
        assertTrue(result.path("userMessageId").asLong()>0);
        assertTrue(result.path("assistantMessageId").asLong()>result.path("userMessageId").asLong());
        assertEquals(2,count(id));
        assertEquals(id,call("GET",sessions(f.course()),f.token(),null,200).path("content").get(0).path("id").asLong());
        // Simulate replacement/deletion of the original published chunk.
        reset(sources,ai,search);
        var page=call("GET",sessions(f.course())+"/"+id+"/messages",f.token(),null,200);
        var user=page.path("content").get(0); var assistant=page.path("content").get(1);
        assertEquals("USER",user.path("role").asText()); assertEquals("Q",user.path("content").asText());
        assertTrue(user.path("status").isNull()); assertEquals(0,user.path("citations").size());
        assertEquals("ASSISTANT",assistant.path("role").asText());
        assertEquals("Giải thích nguyên tử",assistant.path("content").asText());
        assertEquals("test-model",assistant.path("model").asText());
        assertEquals("course-rag-v2",assistant.path("promptVersion").asText());
        assertEquals("Nguyên tử",assistant.path("citations").get(0).path("quote").asText());
        assertTrue(assistant.path("citations").get(0).path("pageStart").isNull());
        call("GET",assistant.path("citations").get(0).path("sourceUrl").asText(),f.token(),null,404);
        assertEquals(2,call("GET",sessions(f.course())+"/"+id+"/messages?size=1&page=1",f.token(),null,200).path("totalElements").asInt());
        verifyNoInteractions(ai,search);
    }

    @Test void insufficientContextIsSavedButStatelessRequestsDoNotWrite() throws Exception {
        var f=fixture(); long id=session(f);
        when(search.search(eq(f.course()),anyLong(),any())).thenReturn(new CourseSearchResponse(f.course(),"Q",List.of()));
        var result=call("POST",chat(f.course()),f.token(),Map.of("question","Q","sessionId",id),200);
        assertEquals("INSUFFICIENT_CONTEXT",result.path("status").asText()); assertEquals(2,count(id));
        var stateless=call("POST",chat(f.course()),f.token(),Map.of("question","Q"),200);
        assertTrue(stateless.path("sessionId").isNull()); assertTrue(stateless.path("userMessageId").isNull());
        assertEquals(2,count(id));
        var body=new HashMap<String,Object>();body.put("question","Q");body.put("sessionId",null);
        call("POST",chat(f.course()),f.token(),body,200);assertEquals(2,count(id));
        var messages=call("GET",sessions(f.course())+"/"+id+"/messages",f.token(),null,200).path("content");
        assertTrue(messages.get(1).path("model").isNull());
        assertEquals(0,messages.get(1).path("citations").size()); verifyNoInteractions(ai);
    }

    @Test void wrongSessionScopeIsRejectedBeforeRetrieval() throws Exception {
        var f=fixture(); var foreign=fixture(); long id=session(f);
        long otherCourse=course(f.token(),f.workspace());
        call("POST",chat(f.course()),foreign.token(),Map.of("question","Q","sessionId",id),404);
        call("POST",chat(otherCourse),f.token(),Map.of("question","Q","sessionId",id),404);
        call("POST",chat(f.course()),f.token(),Map.of("question","Q","sessionId",9223372036854775807L),404);
        assertEquals(0,count(id)); verifyNoInteractions(search,ai);
    }

    @Test void upstreamFailuresAndStaleCitationsDoNotSaveHalfATurn() throws Exception {
        var f=fixture(); long id=session(f); answer(f.course());
        for(HttpStatus status:List.of(HttpStatus.UNPROCESSABLE_ENTITY,HttpStatus.BAD_GATEWAY,HttpStatus.SERVICE_UNAVAILABLE,HttpStatus.GATEWAY_TIMEOUT)) {
            doThrow(new ResponseStatusException(status)).when(ai).answer(anyString(),anyList());
            call("POST",chat(f.course()),f.token(),Map.of("question","Q","sessionId",id),status.value());
            assertEquals(0,count(id));
        }
        reset(ai);answer(f.course());
        when(sources.findPublishedByIds(eq(f.course()),anyLong(),anyList())).thenReturn(List.of());
        call("POST",chat(f.course()),f.token(),Map.of("question","Q","sessionId",id),409);
        assertEquals(0,count(id));
    }

    @Test void sessionIdDoesNotAcceptCoercionOrInvalidNumbers() throws Exception {
        var f=fixture();
        for(Object invalid:List.of(0,-1,"1",true,1.5,new java.math.BigInteger("9223372036854775808")))
            call("POST",chat(f.course()),f.token(),Map.of("question","Q","sessionId",invalid),400);
        verifyNoInteractions(search,ai);
    }
}
