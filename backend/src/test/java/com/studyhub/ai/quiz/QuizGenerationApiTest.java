package com.studyhub.ai.quiz;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class QuizGenerationApiTest extends QuizGenerationApiSupport {
    @Test void generatesReplayAndReadsDraftButCannotPublishOrSubmit() throws Exception {
        var f=fixture();String key=UUID.randomUUID().toString();
        var saved=call("POST",path(f),f.token(),key,body(),201);long quiz=saved.at("/quiz/id").asLong();
        assertEquals("AI_GENERATED",saved.at("/quiz/sourceType").asText());assertEquals("DRAFT",saved.at("/quiz/status").asText());
        assertEquals(4,saved.at("/questions/0/options").size());assertEquals(f.chunk(),saved.at("/generation/evidence/0/citations/0/chunkId").asLong());
        assertEquals(saved,call("POST",path(f),f.token(),key,body(),200));assertEquals(1,quizzes(f));
        assertEquals("DRAFT",call("GET","/api/quizzes/"+quiz,f.token(),null,null,200).path("status").asText());
        assertEquals(1,call("GET","/api/quizzes/"+quiz+"/questions",f.token(),null,null,200).size());
        assertTrue(call("GET","/api/quizzes/"+quiz+"/ai-generation",f.token(),null,null,200).path("sourceCurrent").asBoolean());
        call("PUT","/api/quizzes/"+quiz,f.token(),null,Map.of("title","Published","status","PUBLISHED"),409);
        call("POST","/api/quizzes/"+quiz+"/submit",f.token(),null,Map.of("answers",List.of()),400);
        call("POST",path(f),f.token(),key,Map.of("title","Different","questionCount",1),409);
        jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=?",f.document());
        assertFalse(call("GET","/api/quizzes/"+quiz+"/ai-generation",f.token(),null,null,200).path("sourceCurrent").asBoolean());
        assertEquals(saved,call("POST",path(f),f.token(),key,body(),200));
        call("DELETE","/api/quizzes/"+quiz,f.token(),null,null,204);
        call("POST",path(f),f.token(),key,body(),409);
    }
    @Test void ownershipAndStrictRequestFailBeforeAi() throws Exception {
        var f=fixture();var foreign=fixture();int before=calls.get();
        call("POST",path(f),null,"key",body(),401);
        call("POST",path(f),foreign.token(),"key",body(),404);
        call("POST",path(f),f.token(),null,body(),400);
        call("POST",path(f),f.token(),"has space",body(),400);
        for(Object value:List.of("1",true,1.5,0,11)) call("POST",path(f),f.token(),"key",Map.of("title","Quiz","questionCount",value),400);
        call("POST",path(f),f.token(),"key",Map.of("title","Quiz","status","PUBLISHED"),400);
        call("POST",path(f),f.token(),"key",Map.of("title"," "),400);
        // A literal JSON null is not an empty object and must not reach service dereferences.
        var nullRequest=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+path(f)))
            .header("Content-Type","application/json").header("Authorization","Bearer "+f.token()).header("Idempotency-Key","null")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("null")).build();
        assertEquals(400,http.send(nullRequest,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        call("POST","/api/courses/"+f.course()+"/ai/generate-quiz",f.token(),"key",Map.of("title","Quiz","documentIds",List.of(f.document(),foreign.document())),404);
        assertEquals(before,calls.get());assertEquals(0,quizzes(f));
    }
    @Test void courseSelectionIsRoundRobinAndEvidenceFailureCreatesNothing() throws Exception {
        var f=fixture();long second=document(f.token(),f.owner(),f.course());insertChunk(f.document(),f.version(),1);
        var result=call("POST","/api/courses/"+f.course()+"/ai/generate-quiz",f.token(),"course",Map.of("title","Course","questionCount",1,"documentIds",List.of(second,f.document()),"chunkLimit",2),201);
        assertEquals(2,result.at("/generation/coverage").size());assertEquals(1,result.at("/generation/coverage/0/includedChunkIds").size());
        assertEquals(f.document(),result.at("/generation/coverage/0/documentId").asLong());
        jdbc.update("UPDATE document_chunks SET content='Atomicity [fake-quote]' WHERE document_id=?",f.document());
        call("POST",path(f),f.token(),"bad",body(),502);assertEquals(1,quizzes(f));
        jdbc.update("UPDATE document_chunks SET content='Atomicity [unavailable]' WHERE document_id=?",f.document());
        call("POST",path(f),f.token(),"bad-unavailable",body(),503);assertEquals(1,quizzes(f));
        jdbc.update("UPDATE document_chunks SET content='Atomicity valid' WHERE document_id=?",f.document());
        call("POST",path(f),f.token(),"good-new",body(),201);assertEquals(2,quizzes(f));
    }
}
