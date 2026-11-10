package com.studyhub.ai.quiz;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;

/** Full Spring + PostgreSQL + FastAPI + real provider HTTP adapter; only external LLM is simulated. */
@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="QUIZ_PIPELINE_AI_URL",matches=".+")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class QuizGenerationPipelineIT extends QuizGenerationPostgresSupport {
    static final String SCHEMA="quiz_pipeline_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) throws Exception {database(r,SCHEMA);}
    @AfterAll static void drop() throws Exception {cleanup(SCHEMA);}
    @Test void fullPipelineSavesDraftAndReturnsOriginalOnRetry() throws Exception {
        var f=fixture();var saved=call("POST",path(f),f.token(),"pipeline",body(),201);long quiz=saved.at("/quiz/id").asLong();
        assertEquals("quiz-generation-v1",saved.at("/generation/promptVersion").asText());
        assertEquals(saved,call("POST",path(f),f.token(),"pipeline",body(),200));assertEquals(1,quizzes(f));
        var source=saved.at("/generation/evidence/0/citations/0/sourceUrl").asText();
        assertEquals("Atomicity keeps changes atomic.",call("GET",source,f.token(),null,null,200).path("content").asText());
        var foreign=fixture();call("GET","/api/quizzes/"+quiz+"/ai-generation",foreign.token(),null,null,404);
        call("PUT","/api/quizzes/"+quiz,f.token(),null,Map.of("title","Publish","status","PUBLISHED"),409);
    }
    @Test void failuresDoNotPersistAnyQuizOrIdempotencyKey() throws Exception {
        for(var entry:Map.of("fake-id",502,"fake-quote",502,"malformed",502,"bad-options",502,"budget",422,"insufficient",422,"unavailable",503,"rate-limit",503).entrySet()) {
            var f=fixture();jdbc.update("UPDATE document_chunks SET content=? WHERE id=?","Atomicity ["+entry.getKey()+"]",f.chunk());
            call("POST",path(f),f.token(),"failed",body(),entry.getValue());assertEquals(0,quizzes(f));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM quiz_generations WHERE user_id=?",Integer.class,f.owner()));
        }
    }
    @Test void publicCollectionRunsOnDisposableFixtures() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("RUN_QUIZ_COLLECTION")),"Enable explicit local collection run");
        var f=fixture();long second=document(f.token(),f.owner(),f.course());var foreign=fixture();var stale=fixture();
        var foreignQuiz=call("POST",path(foreign),foreign.token(),"foreign",body(),201);
        jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=?",stale.document());
        var vars=new HashMap<String,String>();vars.put("baseUrl","http://localhost:"+port);vars.put("accessToken",f.token());vars.put("fixtureKey",UUID.randomUUID().toString());
        vars.put("courseId",Long.toString(f.course()));vars.put("documentId",Long.toString(f.document()));vars.put("chunkId",Long.toString(f.chunk()));
        vars.put("foreignAccessToken",foreign.token());vars.put("secondDocumentId",Long.toString(second));vars.put("foreignDocumentId",Long.toString(foreign.document()));vars.put("foreignQuizId",foreignQuiz.at("/quiz/id").asText());
        vars.put("staleDocumentId",Long.toString(stale.document()));vars.put("staleAccessToken",stale.token());
        Path root=Path.of("..").toAbsolutePath().normalize(),environment=Files.createTempFile("quiz-", ".environment.yaml");
        Path output=root.resolve(".superpowers/quiz-collection.log");Files.createDirectories(output.getParent());
        var workerLoop=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        workerLoop.scheduleWithFixedDelay(()->{try{jobWorker.processNext();}catch(RuntimeException ignored){}},0,100,TimeUnit.MILLISECONDS);
        try {
            json.writeValue(environment.toFile(),Map.of("name","Disposable Quiz fixtures","values",vars.entrySet().stream()
                .map(e->Map.of("key",e.getKey(),"value",e.getValue(),"enabled",true,"type",e.getKey().contains("Token")?"secret":"default")).toList()));
            var process=new ProcessBuilder("postman","collection","run",root.resolve("infra/testing/quiz-generation/collection").toString(),"--environment",environment.toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();fail("Collection timed out");}
            assertEquals(0,process.exitValue(),"See .superpowers/quiz-collection.log");assertEquals(2,quizzes(f));
            var jobsProcess=new ProcessBuilder("postman","collection","run",root.resolve("infra/testing/ai-jobs/collection").toString(),"--environment",environment.toString()).directory(root.toFile()).redirectErrorStream(true).redirectOutput(root.resolve(".superpowers/jobs-collection.log").toFile()).start();
            if(!jobsProcess.waitFor(90,TimeUnit.SECONDS)){jobsProcess.destroyForcibly();fail("Job collection timed out");}
            assertEquals(0,jobsProcess.exitValue(),"See .superpowers/jobs-collection.log");assertEquals(2,quizzes(f));
        }finally {workerLoop.shutdownNow();Files.deleteIfExists(environment);}
    }
}
