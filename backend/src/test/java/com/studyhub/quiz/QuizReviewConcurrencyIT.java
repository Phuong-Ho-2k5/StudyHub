package com.studyhub.quiz;

import static org.junit.jupiter.api.Assertions.*;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class QuizReviewConcurrencyIT extends QuizReviewPostgresSupport {
    static final String SCHEMA="review_race_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) throws Exception {database(r,SCHEMA);}
    @AfterAll static void drop() throws Exception {cleanup(SCHEMA);}

    @Test void editRacesNeverPublishUnreviewedContent() throws Exception {
        for(String operation:List.of("review","confirm","publish")) {
            var f=fixture(1);
            if(!operation.equals("review"))reviewAll(f,0);
            if(operation.equals("publish"))action(f,"confirm",0,200);
            String path=f.path()+"/"+(operation.equals("review")?"questions/"+f.questions().getFirst()+"/review":operation);
            var responses=race(()->send("PUT","/api/questions/"+f.questions().getFirst(),f.token(),question("Concurrent edit",0L)),
                    ()->send("POST",path,f.token(),Map.of("expectedRevision",0)));
            var state=get(f);
            if(state.at("/quiz/status").asText().equals("PUBLISHED")) {
                assertEquals(409,responses.get(0).statusCode());assertEquals(200,responses.get(1).statusCode());
                assertEquals(1,state.path("reviewedQuestions").asInt());
                assertEquals("Question 0",state.at("/questions/0/question/text").asText());
            } else {
                assertEquals(200,responses.get(0).statusCode());
                assertTrue(Set.of(200,409).contains(responses.get(1).statusCode()),responses.get(1).body());
                assertEquals("DRAFT",state.at("/quiz/status").asText());
                assertEquals(1,state.path("contentRevision").asInt());assertEquals(0,state.path("reviewedQuestions").asInt());
            }
        }
    }
    @Test void competingConfirmAndPublishAreIdempotent() throws Exception {
        var f=fixture(1);reviewAll(f,0);
        for(String operation:List.of("confirm","publish")) {
            var responses=race(()->send("POST",f.path()+"/"+operation,f.token(),Map.of("expectedRevision",0)),
                    ()->send("POST",f.path()+"/"+operation,f.token(),Map.of("expectedRevision",0)));
            for(var response:responses)assertEquals(200,response.statusCode(),response.body());
            String timestamp=operation.equals("confirm")?"confirmedAt":"publishedAt";
            assertEquals(JSON.readTree(responses.get(0).body()).path(timestamp),JSON.readTree(responses.get(1).body()).path(timestamp));
        }
    }
    @Test void editWaitingOnParentLockReadsCurrentRevisionRatherThanCachedEntity() throws Exception {
        var f=fixture(1);reviewAll(f,0);action(f,"confirm",0,200);
        var executor=Executors.newSingleThreadExecutor();
        try(var c=connection()) {
            c.setAutoCommit(false);
            try(var stmt=c.prepareStatement("SELECT id FROM "+SCHEMA+".quizzes WHERE id=? FOR UPDATE")) {stmt.setLong(1,f.quiz());stmt.executeQuery();}
            var started=new CountDownLatch(1);
            var pending=executor.submit(()->{started.countDown();return send("PUT","/api/questions/"+f.questions().getFirst(),f.token(),question("Late edit",0L));});
            assertTrue(started.await(5,TimeUnit.SECONDS));
            // Wait for PostgreSQL to report the application connection blocked on our row lock.
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
            while(System.nanoTime()<deadline) {
                waiting=jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%quizzes%'",Integer.class)>0;
                if(waiting)break;
                Thread.sleep(20);
            }
            assertTrue(waiting,"Expected edit to wait on parent Quiz lock");
            try(var stmt=c.prepareStatement("UPDATE "+SCHEMA+".quizzes SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP WHERE id=?")) {stmt.setLong(1,f.quiz());stmt.executeUpdate();}
            c.commit();
            var response=pending.get(10,TimeUnit.SECONDS);assertEquals(409,response.statusCode(),response.body());
            assertEquals("Question 0",get(f).at("/questions/0/question/text").asText());
        } finally {executor.shutdownNow();}
    }
    private List<HttpResponse<String>> race(Callable<HttpResponse<String>> first,Callable<HttpResponse<String>> second) throws Exception {
        var executor=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);
        try {
            var a=executor.submit(()->{gate.await(5,TimeUnit.SECONDS);return first.call();});
            var b=executor.submit(()->{gate.await(5,TimeUnit.SECONDS);return second.call();});
            return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        } finally {executor.shutdownNow();}
    }
}
