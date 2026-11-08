package com.studyhub.ai.feedback;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import jakarta.persistence.EntityManagerFactory;

@TestPropertySource(properties="spring.jpa.properties.hibernate.generate_statistics=true")
class AIFeedbackHistoryTest extends AIFeedbackTestSupport {
    @Autowired EntityManagerFactory emf;
    @Test void historyReturnsOnlyOwnFeedbackForCurrentPage() throws Exception {
        var f=fixture();var other=fixture();var a=save(f,Map.of("rating","HELPFUL","note","Saved"),201);
        save(other,Map.of("rating","NOT_HELPFUL"),201);
        var page=call("GET",history(f)+"?size=1&page=0",f.token(),null,200);
        assertTrue(page.path("content").get(0).path("feedback").isNull());
        page=call("GET",history(f)+"?size=1&page=1",f.token(),null,200);
        assertEquals(a,page.path("content").get(0).path("feedback"));
        assertEquals(f.assistant(),page.path("content").get(0).path("id").asLong());
        long fresh=message(f.session(),"ASSISTANT","INSUFFICIENT_CONTEXT");
        page=call("GET",history(f),f.token(),null,200);
        assertEquals(fresh,page.path("content").get(2).path("id").asLong());
        assertTrue(page.path("content").get(2).path("feedback").isNull());
        call("GET",history(f),other.token(),null,404);
    }
    @Test void historyBatchReadHasNoNPlusOne() throws Exception {
        var f=fixture();
        for(int i=0;i<100;i++)message(f.session(),"ASSISTANT","INSUFFICIENT_CONTEXT");
        save(f,Map.of("rating","HELPFUL"),201);
        var stats=emf.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class).getStatistics();
        stats.clear();call("GET",history(f)+"?size=20",f.token(),null,200);
        long twenty=stats.getPrepareStatementCount();
        stats.clear();call("GET",history(f)+"?size=100",f.token(),null,200);
        assertEquals(twenty,stats.getPrepareStatementCount());
        assertEquals(1,java.util.Arrays.stream(stats.getQueries()).filter(q->q.contains("AIFeedback")).count());
        // A page containing only USER messages must not issue the feedback SELECT.
        stats.clear();call("GET",history(f)+"?size=1",f.token(),null,200);
        assertEquals(twenty-1,stats.getPrepareStatementCount());
    }
}
