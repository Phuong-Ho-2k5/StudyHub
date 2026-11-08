package com.studyhub.ai.feedback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.studyhub.ai.ChatHistoryService;
import com.studyhub.ai.dto.CourseChatResponse;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class AIFeedbackConcurrencyIT extends AIFeedbackPostgresSupport {
    @MockitoSpyBean FeedbackMessageScopeRepository scope;
    @Autowired ChatHistoryService history;
    @Test void concurrentFirstWritesUpdatesAndRetryAreAtomic() throws Exception {
        var f=fixture();var barrier=new CyclicBarrier(2);
        doAnswer(i->{var value=i.callRealMethod();barrier.await(10,TimeUnit.SECONDS);return value;}).when(scope).findOwnedScope(f.assistant(),f.user());
        var first=Map.of("rating","HELPFUL","note","First");var second=Map.of("rating","NOT_HELPFUL","note","Second");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->send("POST",feedback(f.assistant()),f.token(),first));
            var b=pool.submit(()->send("POST",feedback(f.assistant()),f.token(),second));
            var statuses=new ArrayList<>(List.of(a.get(20,TimeUnit.SECONDS).statusCode(),b.get(20,TimeUnit.SECONDS).statusCode()));
            Collections.sort(statuses);assertEquals(List.of(200,201),statuses);
            a=pool.submit(()->send("POST",feedback(f.assistant()),f.token(),first));
            b=pool.submit(()->send("POST",feedback(f.assistant()),f.token(),second));
            assertEquals(200,a.get(20,TimeUnit.SECONDS).statusCode());assertEquals(200,b.get(20,TimeUnit.SECONDS).statusCode());
        }
        reset(scope);
        assertEquals(1,jdbc.queryForObject("select count(*) from ai_feedback where message_id=?",Integer.class,f.assistant()));
        var row=jdbc.queryForMap("select rating,note from ai_feedback where message_id=?",f.assistant());
        assertTrue(row.get("rating").equals("HELPFUL")?row.get("note").equals("First"):row.get("note").equals("Second"));
        var saved=save(f,first,200);assertEquals(saved,save(f,first,200));
    }
    @Test void feedbackAndAppendTurnDoNotDeadlock() throws Exception {
        var f=fixture();var start=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return save(f,Map.of("rating","HELPFUL"),201);});
            var b=pool.submit(()->{start.await();return history.appendTurn(f.course(),f.user(),f.session(),
                new CourseChatResponse(f.course(),"Q","INSUFFICIENT_CONTEXT","No context",null,"v1",List.of(),null,null,null));});
            a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
        }
        assertEquals(4,jdbc.queryForObject("select count(*) from chat_messages where session_id=?",Integer.class,f.session()));
    }
    @ParameterizedTest @ValueSource(strings={"message","session","course","user"})
    void deletionAfterScopeReadReturns404WithoutOrphans(String target) throws Exception {
        var f=fixture();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(i->{var value=i.callRealMethod();entered.countDown();assertTrue(release.await(15,TimeUnit.SECONDS));return value;}).when(scope).findOwnedScope(f.assistant(),f.user());
        try(var pool=Executors.newSingleThreadExecutor()) {
            var pending=pool.submit(()->send("POST",feedback(f.assistant()),f.token(),Map.of("rating","HELPFUL")));
            try {assertTrue(entered.await(10,TimeUnit.SECONDS));delete(f,target);} finally {release.countDown();}
            assertEquals(404,pending.get(20,TimeUnit.SECONDS).statusCode());
        }
        assertEquals(0,jdbc.queryForObject("select count(*) from ai_feedback where message_id=?",Integer.class,f.assistant()));
    }
    @ParameterizedTest @ValueSource(strings={"message","session","course","user"})
    void deletionAfterRecheckSerializesWithSave(String target) throws Exception {
        var f=fixture();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(i->{var value=i.callRealMethod();entered.countDown();assertTrue(release.await(15,TimeUnit.SECONDS));return value;})
            .when(scope).findOwnedScopeInSession(f.assistant(),f.session(),f.course(),f.user());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var pending=pool.submit(()->send("POST",feedback(f.assistant()),f.token(),Map.of("rating","HELPFUL")));
            Future<?> deletion=null;
            try {
                assertTrue(entered.await(10,TimeUnit.SECONDS));deletion=pool.submit(()->delete(f,target));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                boolean waiting=false;
                while(System.nanoTime()<deadline && !waiting) {
                    waiting=jdbc.queryForObject("select count(*)>0 from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like 'delete from %'",Boolean.class);
                    Thread.onSpinWait();
                }
                assertTrue(waiting,"Deletion must reach a database lock before save continues");
            } finally {release.countDown();}
            var response=pending.get(20,TimeUnit.SECONDS);
            assertEquals(201,response.statusCode(),response.body());
            if(deletion!=null)deletion.get(20,TimeUnit.SECONDS);
        }
        assertEquals(0,jdbc.queryForObject("select count(*) from ai_feedback where message_id=?",Integer.class,f.assistant()));
    }
    private void delete(Fixture f,String target) {
        String table=switch(target){case "message"->"chat_messages";case "session"->"chat_sessions";case "course"->"courses";default->"users";};
        long id=switch(target){case "message"->f.assistant();case "session"->f.session();case "course"->f.course();default->f.user();};
        jdbc.update("delete from "+table+" where id=?",id);
    }
}
