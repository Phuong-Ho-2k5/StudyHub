package com.studyhub.ai.concept;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;

/** PostgreSQL + Spring HTTP + FastAPI + real provider HTTP adapter; only OpenAI is simulated. */
@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="CONCEPT_PIPELINE_AI_URL",matches=".+")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ConceptSuggestionPipelineIT extends ConceptPostgresSupport {
    static final String SCHEMA="concept_pipeline_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) throws Exception { database(registry,SCHEMA); }
    @AfterAll static void drop() throws Exception { cleanup(SCHEMA); }
    @Test void fullPipelineOpensSourceAndOnlySavesAfterConfirmation() throws Exception {
        var f=fixture();var other=fixture();var batch=generate(f);
        assertEquals(0,concepts(f));assertEquals(f.chunk(),batch.at("/suggestions/0/citations/0/chunkId").asLong());
        assertEquals("document-concepts-v1",batch.path("promptVersion").asText());
        String source=batch.at("/suggestions/0/citations/0/sourceUrl").asText();
        assertEquals("Atomicity nguyên tử",call("GET",source,f.token(),null,200).path("content").asText());
        call("GET",source,other.token(),null,404);
        var approved=Map.of("name","Atomicity reviewed","description","Reviewed description");
        var saved=call("POST",confirm(f,batch),f.token(),approved,201);
        assertEquals(0,saved.at("/concept/confidence").asInt());assertEquals("NEW",saved.at("/concept/status").asText());
        assertEquals(saved,call("POST",confirm(f,batch),f.token(),approved,200));assertEquals(1,concepts(f));
        var pending=generate(f);
        jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=?",f.document());
        call("POST",confirm(f,pending),f.token(),Map.of("name","Second","description","Reviewed"),409);
        assertFalse(call("GET",path(f)+"/"+pending.path("id").asText(),f.token(),null,200).path("sourceCurrent").asBoolean());
    }
    @Test void realFastApiRejectsInvalidEvidenceAndBudgetWithoutDrafts() throws Exception {
        for(var entry:Map.of("fake-id",502,"fake-quote",502,"malformed",502,"budget",422,"unavailable",503).entrySet()) {
            var f=fixture();jdbc.update("UPDATE document_chunks SET content=? WHERE id=?","Atomicity ["+entry.getKey()+"]",f.chunk());
            call("POST",path(f),f.token(),Map.of(),entry.getValue());assertEquals(0,concepts(f));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM concept_suggestion_batches WHERE document_id=?",Integer.class,f.document()));
        }
    }
    @Test void publicCollectionRunsAgainstDisposableFixtures() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("RUN_CONCEPT_COLLECTION")),"Enable explicit local collection run");
        var f=fixture();var foreign=fixture();var stale=fixture();var staleBatch=generate(stale);
        jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=?",stale.document());
        var variables=new HashMap<String,String>();variables.put("baseUrl","http://localhost:"+port);variables.put("accessToken",f.token());
        variables.put("courseId",Long.toString(f.course()));variables.put("documentId",Long.toString(f.document()));
        variables.put("foreignCourseId",Long.toString(foreign.course()));variables.put("foreignDocumentId",Long.toString(foreign.document()));
        variables.put("staleCourseId",Long.toString(stale.course()));variables.put("staleDocumentId",Long.toString(stale.document()));
        // Same owner is needed to distinguish stale 409 from foreign 404.
        variables.put("staleAccessToken",stale.token());variables.put("staleBatchId",staleBatch.path("id").asText());
        variables.put("staleItemId",staleBatch.at("/suggestions/0/id").asText());
        variables.put("approvedName","Reviewed-"+UUID.randomUUID());
        Path root=Path.of("..").toAbsolutePath().normalize(),environment=Files.createTempFile("concept-", ".environment.yaml");
        Path output=root.resolve(".superpowers/concept-collection.log");
        try {
            json.writeValue(environment.toFile(),Map.of("name","Disposable Concept fixture","values",variables.entrySet().stream()
                .map(e->Map.of("key",e.getKey(),"value",e.getValue(),"enabled",true,"type",e.getKey().contains("Token")?"secret":"default")).toList()));
            var process=new ProcessBuilder("postman","collection","run",root.resolve("infra/testing/concepts/collection").toString(),"--environment",environment.toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if(!process.waitFor(90,TimeUnit.SECONDS)) {process.destroyForcibly();fail("Collection timed out");}
            assertEquals(0,process.exitValue(),"See .superpowers/concept-collection.log");assertEquals(1,concepts(f));
        } finally { Files.deleteIfExists(environment); }
    }
}
