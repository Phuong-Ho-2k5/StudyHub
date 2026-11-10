package com.studyhub.ai.quiz;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.studyhub.ai.quiz.QuizGenerationModels.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class QuizGenerationConcurrencyIT extends QuizGenerationPostgresSupport {
    static final String SCHEMA="quiz_concurrent_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) throws Exception {database(r,SCHEMA);}
    @AfterAll static void drop() throws Exception {cleanup(SCHEMA);}
    @MockitoBean QuizGenerationClient client;
    GeneratedResult output(SourceWindow window) {
        return new GeneratedResult("gpt-4.1-mini",PROMPT_VERSION,List.of(new GeneratedQuestion("What is Atomicity?",List.of(new Option("A",true),new Option("B",false),new Option("C",false),new Option("D",false)),List.of(new Evidence(window.contexts().getFirst().chunkId(),"Atomicity")))));
    }
    @Test void concurrentSameKeyReturnsOneJobAndOnlyOneWorkerCallsAi() throws Exception {
        var f=fixture();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(client.generate(any(),eq(1))).thenAnswer(invocation->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());entered.countDown();assertTrue(release.await(15,TimeUnit.SECONDS));return output(invocation.getArgument(0));});
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->send("POST",path(f),f.token(),"same",body()));
            var b=pool.submit(()->send("POST",path(f),f.token(),"same",body()));
            var ra=a.get(20,TimeUnit.SECONDS);var rb=b.get(20,TimeUnit.SECONDS);
            assertEquals(202,ra.statusCode());assertEquals(202,rb.statusCode());assertEquals(json.readTree(ra.body()),json.readTree(rb.body()));assertEquals(0,quizzes(f));
            var id=UUID.fromString(json.readTree(ra.body()).path("jobId").asText());
            var running=pool.submit(()->jobWorker.process(id));
            try {assertTrue(entered.await(15,TimeUnit.SECONDS));assertFalse(jobWorker.process(id));}finally {release.countDown();}
            assertTrue(running.get(20,TimeUnit.SECONDS));assertEquals(1,quizzes(f));
            assertEquals(com.studyhub.ai.job.AIJobStatus.COMPLETED,jobs.get(id,f.owner()).status());
            verify(client,times(1)).generate(any(),eq(1));
        }
    }
    @Test void sameKeyDifferentPayloadCannotEnqueueTwoCommands() throws Exception {
        var f=fixture();when(client.generate(any(),eq(1))).thenAnswer(invocation->output(invocation.getArgument(0)));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->send("POST",path(f),f.token(),"same",body()));
            var b=pool.submit(()->send("POST",path(f),f.token(),"same",Map.of("title","Different","questionCount",1)));
            var ra=a.get(20,TimeUnit.SECONDS);var rb=b.get(20,TimeUnit.SECONDS);
            assertEquals(Set.of(202,409),Set.of(ra.statusCode(),rb.statusCode()));
            var id=UUID.fromString(json.readTree(ra.statusCode()==202?ra.body():rb.body()).path("jobId").asText());jobWorker.process(id);assertEquals(1,quizzes(f));
        }
    }
    @Test void sourceChangeAndCourseDeletionDuringAiCannotCreateDraft() throws Exception {
        for(boolean delete:List.of(false,true)) {
            var f=fixture();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            when(client.generate(any(),eq(1))).thenAnswer(invocation->{entered.countDown();assertTrue(release.await(15,TimeUnit.SECONDS));return output(invocation.getArgument(0));});
            var accepted=call("POST",path(f),f.token(),"race",body(),202);var id=UUID.fromString(accepted.path("jobId").asText());
            try(var pool=Executors.newSingleThreadExecutor()) {
                var pending=pool.submit(()->jobWorker.process(id));
                try {
                    assertTrue(entered.await(15,TimeUnit.SECONDS));
                    if(delete)call("DELETE","/api/courses/"+f.course(),f.token(),null,null,204);
                    else jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=?",f.document());
                }finally {release.countDown();}
                assertTrue(pending.get(20,TimeUnit.SECONDS));assertEquals(0,quizzes(f));
                if(delete)call("GET","/api/ai/jobs/"+id,f.token(),null,null,404);
                else assertEquals(com.studyhub.ai.job.AIJobErrorCode.SOURCE_CHANGED,jobs.get(id,f.owner()).errorCode());
            }
        }
    }
}
