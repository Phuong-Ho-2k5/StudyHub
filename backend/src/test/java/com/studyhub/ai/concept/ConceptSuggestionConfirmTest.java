package com.studyhub.ai.concept;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ConceptSuggestionConfirmTest extends ConceptSuggestionApiSupport {
    final Map<String,String> approved=Map.of("name","Atomicity","description","Đã duyệt");
    @Test void confirmCreatesNewUnlearnedConceptAndRetryDoesNotDuplicate() throws Exception {
        var f=fixture();var batch=generate(f);String endpoint=confirm(f,batch);
        var result=call("POST",endpoint,f.token(),Map.of("name"," Atomicity ","description"," Đã duyệt "),201);
        assertEquals("Atomicity",result.at("/concept/name").asText());assertEquals("NEW",result.at("/concept/status").asText());
        assertEquals(0,result.at("/concept/confidence").asInt());assertEquals(1,concepts(f));
        assertEquals(result,call("POST",endpoint,f.token(),approved,200));
        call("POST",endpoint,f.token(),Map.of("name","Other","description","Đã duyệt"),409);
        var refreshed=call("GET",path(f)+"/"+batch.path("id").asText(),f.token(),null,200);
        assertEquals("CONFIRMED",refreshed.at("/suggestions/0/state").asText());
        call("DELETE","/api/concepts/"+result.at("/concept/id").asLong(),f.token(),null,204);
        call("POST",endpoint,f.token(),approved,409);assertEquals(0,concepts(f));
    }
    @Test void duplicateNameAndStalePublicationDoNotConfirm() throws Exception {
        var f=fixture();var batch=generate(f);String endpoint=confirm(f,batch);
        jdbc.update("INSERT INTO concepts(course_id,name,description) VALUES (?,'Atomicity','Existing')",f.course());
        call("POST",endpoint,f.token(),approved,409);
        assertEquals("PENDING",jdbc.queryForObject("SELECT state FROM concept_suggestions WHERE batch_id=?",String.class,batch.path("id").asText()));
        jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=?",f.document());
        call("POST",endpoint,f.token(),Map.of("name","Other","description","Reviewed"),409);
        assertFalse(call("GET",path(f)+"/"+batch.path("id").asText(),f.token(),null,200).path("sourceCurrent").asBoolean());
        assertEquals(1,concepts(f));
    }
    @Test void confirmsAreScopedAndCannotOverrideLearningOrProvenance() throws Exception {
        var f=fixture();var other=fixture();String endpoint=confirm(f,generate(f));
        call("POST",endpoint,other.token(),approved,404);
        for(var body:List.of(Map.of("name"," ","description","Reviewed"),Map.of("name","x".repeat(151),"description","Reviewed"),
            Map.of("name","Atomicity","description","Reviewed","confidence",100),Map.of("name",123,"description","Reviewed")))
            call("POST",endpoint,f.token(),body,400);
        assertEquals(0,concepts(f));
    }
    @Test void updateFailureRollsBackConceptCreation() throws Exception {
        var f=fixture();var batch=generate(f);long id=batch.at("/suggestions/0/id").asLong();
        jdbc.execute("ALTER TABLE concept_suggestions ADD CONSTRAINT reject_confirm_"+id+" CHECK(id <> "+id+" OR state='PENDING')");
        try { call("POST",confirm(f,batch),f.token(),approved,409);assertEquals(0,concepts(f)); }
        finally { jdbc.execute("ALTER TABLE concept_suggestions DROP CONSTRAINT reject_confirm_"+id); }
    }
    @Test void simultaneousRetriesCreateOnlyOneConcept() throws Exception {
        var f=fixture();String endpoint=confirm(f,generate(f));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->send("POST",endpoint,f.token(),approved).statusCode());
            var b=pool.submit(()->send("POST",endpoint,f.token(),approved).statusCode());
            var codes=new HashSet<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));
            assertEquals(Set.of(200,201),codes);assertEquals(1,concepts(f));
        }
    }
}
