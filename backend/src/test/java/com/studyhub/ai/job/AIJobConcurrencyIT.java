package com.studyhub.ai.job;

import static com.studyhub.ai.job.AIJobModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
class AIJobConcurrencyIT extends AIJobPostgresSupport {
    static final String schema="jobs_race_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {database(registry,schema);}
    @AfterAll static void drop() throws Exception {cleanup(schema);}
    @Autowired AIJobService service;
    @MockitoSpyBean AIJobRepository jobs;
    @AfterEach void clearSpies() {reset(jobs);}
    UUID enqueue(Fixture f) {return service.enqueue(new CreateAIJob(AIJobType.INGEST_DOCUMENT,f.course(),f.document()),f.user());}
    AIJobResult result() {return new AIJobResult(AIJobResultType.DOCUMENT_INDEX,"index");}
    @Test void concurrentClaimsHaveExactlyOneWinner() throws Exception {
        var f=fixture();var id=enqueue(f);var barrier=new CyclicBarrier(2);
        doAnswer(i->{var value=i.callRealMethod();barrier.await(15,TimeUnit.SECONDS);return value;}).when(jobs).find(id);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->service.claim(id));var b=pool.submit(()->service.claim(id));
            assertEquals(1,(a.get(20,TimeUnit.SECONDS).isPresent()?1:0)+(b.get(20,TimeUnit.SECONDS).isPresent()?1:0));
        }
        assertEquals(AIJobStatus.PROCESSING,service.get(id,f.user()).status());
    }
    @Test void completionAndFailureCannotOverwriteEachOther() throws Exception {
        var f=fixture();var id=enqueue(f);var claim=service.claim(id).orElseThrow();var barrier=new CyclicBarrier(2);
        doAnswer(i->{var value=i.callRealMethod();barrier.await(15,TimeUnit.SECONDS);return value;}).when(jobs).find(id);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->service.complete(claim,result()));var b=pool.submit(()->service.fail(claim,AIJobErrorCode.AI_TIMEOUT));
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
        reset(jobs);var snapshot=service.get(id,f.user());
        assertTrue(Set.of(AIJobStatus.COMPLETED,AIJobStatus.FAILED).contains(snapshot.status()));
        assertFalse(service.complete(claim,result()));assertFalse(service.fail(claim,AIJobErrorCode.JOB_FAILED));assertEquals(snapshot,service.get(id,f.user()));
    }
    @ParameterizedTest @ValueSource(strings={"course","document","user","owner"})
    void scopeChangeBeforeLocksRejectsPublication(String target) throws Exception {
        var f=fixture();var other=fixture();var id=enqueue(f);var claim=service.claim(id).orElseThrow();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(i->{var value=i.callRealMethod();entered.countDown();assertTrue(release.await(15,TimeUnit.SECONDS));return value;}).when(jobs).find(id);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var pending=pool.submit(()->publish(f,claim));
            try {assertTrue(entered.await(10,TimeUnit.SECONDS));change(f,other,target);} finally {release.countDown();}
            assertFalse(pending.get(20,TimeUnit.SECONDS));
        }
        assertEquals(0,jdbc.queryForObject("select count(*) from quizzes where title=?",Integer.class,id.toString()));
        assertEquals(target.equals("owner")?1:0,jdbc.queryForObject("select count(*) from ai_jobs where id=?",Integer.class,id.toString()));
        if(target.equals("owner"))assertEquals("PROCESSING",jdbc.queryForObject("select status from ai_jobs where id=?",String.class,id.toString()));
    }
    @ParameterizedTest @ValueSource(strings={"course","document","user","owner"})
    void scopeChangeAfterLocksWaitsForPublication(String target) throws Exception {
        var f=fixture();var other=fixture();var id=enqueue(f);var claim=service.claim(id).orElseThrow();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(i->{var value=i.callRealMethod();entered.countDown();assertTrue(release.await(15,TimeUnit.SECONDS));return value;}).when(jobs).lock(id);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var pending=pool.submit(()->publish(f,claim));Future<?> change=null;
            try {
                assertTrue(entered.await(10,TimeUnit.SECONDS));
                var pid=new java.util.concurrent.atomic.AtomicInteger();var connected=new CountDownLatch(1);
                change=pool.submit(()->tx().executeWithoutResult(status->{
                    pid.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));connected.countDown();change(f,other,target);
                }));
                assertTrue(connected.await(10,TimeUnit.SECONDS));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
                while(System.nanoTime()<deadline && !waiting) {
                    waiting=Boolean.TRUE.equals(jdbc.queryForObject("select count(*)>0 from pg_stat_activity where pid=? and wait_event_type='Lock'",Boolean.class,pid.get()));Thread.onSpinWait();
                }
                assertTrue(waiting,"Scope mutation must wait on the publication locks");
            } finally {release.countDown();}
            assertTrue(pending.get(20,TimeUnit.SECONDS));if(change!=null)change.get(20,TimeUnit.SECONDS);
        }
        assertEquals(target.equals("owner")?1:0,jdbc.queryForObject("select count(*) from ai_jobs where id=?",Integer.class,id.toString()));
        assertEquals(target.equals("owner") || target.equals("document")?1:0,jdbc.queryForObject("select count(*) from quizzes where title=?",Integer.class,id.toString()));
    }
    /** Simulates the future caller: completion obtains locks before output is inserted. */
    boolean publish(Fixture f,AIJobClaim claim) {
        return Boolean.TRUE.equals(tx().execute(status->{
            if(!service.complete(claim,result())) {status.setRollbackOnly();return false;}
            jdbc.update("insert into quizzes(course_id,title) values (?,?)",f.course(),claim.jobId().toString());
            return true;
        }));
    }
    @Test void publicationAndCompletionRollbackTogetherAndRejectedClaimRollsBackOutput() {
        var f=fixture();var id=enqueue(f);var claim=service.claim(id).orElseThrow();
        tx().executeWithoutResult(status->{assertTrue(publish(f,claim));status.setRollbackOnly();});
        assertEquals(AIJobStatus.PROCESSING,service.get(id,f.user()).status());
        assertEquals(0,jdbc.queryForObject("select count(*) from quizzes where title=?",Integer.class,id.toString()));
        tx().executeWithoutResult(status->{
            jdbc.update("insert into quizzes(course_id,title) values (?,?)",f.course(),id.toString());
            if(!service.complete(new AIJobClaim(id,UUID.randomUUID()),result()))status.setRollbackOnly();
            else fail("Stale claim must reject completion");
        });
        assertEquals(0,jdbc.queryForObject("select count(*) from quizzes where title=?",Integer.class,id.toString()));
        assertEquals(AIJobStatus.PROCESSING,service.get(id,f.user()).status());
    }
    void change(Fixture f,Fixture other,String target) {
        switch(target) {
            case "owner"->jdbc.update("update workspaces set owner_id=? where id=?",other.user(),f.workspace());
            case "user"->jdbc.update("delete from users where id=?",f.user());
            case "course"->jdbc.update("delete from courses where id=?",f.course());
            default->jdbc.update("delete from documents where id=?",f.document());
        }
    }
}
