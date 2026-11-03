package com.studyhub.quiz;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class QuizReviewEvidenceTest extends QuizReviewTestSupport {
    @Test void reviewUsesCurrentQuestionsAndLabelsEvidenceAsOriginalAfterEditsAndSourceChanges() throws Exception {
        var f=fixture(1);long q=f.questions().getFirst();
        var originalQuiz=call("GET",f.path(),f.token(),null,200);
        var originalQuestions=call("GET",f.path()+"/questions",f.token(),null,200);
        long doc=call("POST","/api/courses/"+f.course()+"/documents",f.token(),Map.of("title","Source","fileName","test.pdf","fileType","application/pdf","storagePath","legacy/test.pdf"),201).path("id").asLong();
        String ingestion=UUID.randomUUID().toString(),generation=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO document_ingestions(id,document_id,requested_by,status) VALUES (?,?,?,'INDEXED')",ingestion,doc,f.owner());
        jdbc.update("UPDATE documents SET published_ingestion_id=? WHERE id=?",ingestion,doc);
        var coverage=List.of(Map.of("documentId",doc,"ingestionId",ingestion,"includedChunkIds",List.of(123L)));
        var citation=new LinkedHashMap<String,Object>();
        citation.put("chunkId",123L);citation.put("documentId",doc);citation.put("documentTitle","Source");citation.put("chunkIndex",0);
        citation.put("pageStart",1);citation.put("pageEnd",1);citation.put("quote","Original source quote");citation.put("sourceUrl","/api/source/fixture");citation.put("fileUrl",null);
        String snapshot=JSON.writeValueAsString(Map.of("quiz",originalQuiz,"questions",originalQuestions,"generation",Map.of("id",generation,"model","fixture","promptVersion","quiz-generation-v1","coverage",coverage,"evidence",List.of(Map.of("questionId",q,"citations",List.of(citation))),"sourceCurrent",true)));
        jdbc.update("INSERT INTO quiz_generations(id,user_id,request_key,fingerprint,quiz_id,model,prompt_version,coverage_json,response_json) VALUES (?,?,?,'fixture',?,'fixture','quiz-generation-v1',?,?)",generation,f.owner(),generation,f.quiz(),JSON.writeValueAsString(coverage),snapshot);
        var state=get(f);assertTrue(state.path("sourceCurrent").asBoolean());
        assertFalse(state.at("/questions/0/editedSinceGeneration").asBoolean());
        call("PUT","/api/questions/"+q,f.token(),question("Human corrected question",0L),200);
        call("POST",f.path()+"/questions",f.token(),question("Human added",1L),201);
        jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=?",doc);
        state=get(f);assertFalse(state.path("sourceCurrent").asBoolean());
        assertEquals("Human corrected question",state.at("/questions/0/question/text").asText());
        assertTrue(state.at("/questions/0/editedSinceGeneration").asBoolean());
        assertEquals("ORIGINAL_GENERATION",state.at("/questions/0/evidenceOrigin").asText());
        assertEquals("Original source quote",state.at("/questions/0/citations/0/quote").asText());
        assertEquals("NONE",state.at("/questions/1/evidenceOrigin").asText());
        assertTrue(state.at("/questions/1/editedSinceGeneration").asBoolean());
        assertEquals(0,state.at("/questions/1/citations").size());
        for(var row:state.path("questions"))action(f,"questions/"+row.at("/question/id").asLong()+"/review",2,200);
        action(f,"confirm",2,200);action(f,"publish",2,200);
        assertEquals(snapshot,jdbc.queryForObject("SELECT response_json FROM quiz_generations WHERE id=?",String.class,generation));
    }
}
