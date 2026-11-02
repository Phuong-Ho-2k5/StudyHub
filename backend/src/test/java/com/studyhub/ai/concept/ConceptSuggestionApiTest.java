package com.studyhub.ai.concept;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ConceptSuggestionApiTest extends ConceptSuggestionApiSupport {
    @Test void generateStoresDraftAndVerifiedSourceButNoConcept() throws Exception {
        var f=fixture();var batch=generate(f);
        assertEquals(0,concepts(f));assertTrue(batch.path("sourceCurrent").asBoolean());
        assertEquals(f.chunk(),batch.at("/coverage/includedChunkIds/0").asLong());
        assertTrue(batch.at("/coverage/nextChunkIndex").isNull());
        var item=batch.path("suggestions").get(0);
        assertEquals("PENDING",item.path("state").asText());
        String quote=item.at("/citations/0/quote").asText();
        assertFalse(quote.isBlank());assertTrue("Atomicity nguyên tử".contains(quote));
        assertEquals(f.document(),item.at("/citations/0/documentId").asLong());
        assertEquals(batch,call("GET",path(f)+"/"+batch.path("id").asText(),f.token(),null,200));
    }
    @Test void validatesTypesLimitsAndScope() throws Exception {
        var f=fixture();var other=fixture();
        call("POST",path(f),null,Map.of(),401);
        call("POST",path(f),other.token(),Map.of(),404);
        for(Object invalid:List.of(Map.of("chunkLimit",true),Map.of("chunkLimit","5"),Map.of("chunkLimit",1.5),Map.of("chunkLimit",0),
            Map.of("chunkLimit",21),Map.of("maxSuggestions",11),Map.of("startChunkIndex",-1),Map.of("userId",f.owner()),List.of()))
            call("POST",path(f),f.token(),invalid,400);
        var nullBody=new HashMap<String,Object>();nullBody.put("chunkLimit",null);
        call("POST",path(f),f.token(),nullBody,400);
        call("POST",path(f),f.token(),Map.of("startChunkIndex",Integer.MAX_VALUE),422);
        var batch=generate(f);
        call("GET",path(f)+"/"+batch.path("id").asText(),other.token(),null,404);
        call("GET",path(other)+"/"+batch.path("id").asText(),f.token(),null,404);
    }
    @Test void falseEvidenceAndUpstreamErrorsDoNotLeaveDrafts() throws Exception {
        for(String marker:List.of("[fake-id]","[fake-quote]","[unavailable]")) {
            var f=fixture();jdbc.update("UPDATE document_chunks SET content=? WHERE id=?","Atomicity "+marker,f.chunk());
            call("POST",path(f),f.token(),Map.of(),marker.equals("[unavailable]")?503:502);
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM concept_suggestion_batches WHERE document_id=?",Integer.class,f.document()));
            assertEquals(0,concepts(f));
        }
    }
    @Test void emptyOutputAndUnpublishedDocument() throws Exception {
        var f=fixture();jdbc.update("UPDATE document_chunks SET content='[empty]' WHERE id=?",f.chunk());
        assertTrue(generate(f).path("suggestions").isEmpty());
        jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=?",f.document());
        call("POST",path(f),f.token(),Map.of(),409);
    }
}
