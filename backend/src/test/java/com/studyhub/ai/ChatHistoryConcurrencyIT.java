package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.studyhub.document.DocumentSourceRepository;
import com.studyhub.search.CourseSearchService;
import com.studyhub.search.dto.CourseSearchResponse;
import com.studyhub.search.dto.CourseSearchResponse.SearchHitResponse;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ChatHistoryConcurrencyIT extends ChatHistoryTestSupport {
    static final String SCHEMA="chat_concurrent_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        try(var connection=DriverManager.getConnection(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));var sql=connection.createStatement()) {
            sql.execute("CREATE SCHEMA "+SCHEMA);
        }
        String url=System.getenv("PGVECTOR_TEST_URL");
        registry.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+SCHEMA+",public");
        registry.add("spring.datasource.username",()->System.getenv("PGVECTOR_TEST_USER"));
        registry.add("spring.datasource.password",()->System.getenv("PGVECTOR_TEST_PASSWORD"));
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.flyway.default-schema",()->SCHEMA);
    }
    @AfterAll static void cleanup() throws Exception {
        try(var connection=DriverManager.getConnection(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));var sql=connection.createStatement()) {
            sql.execute("DROP SCHEMA "+SCHEMA+" CASCADE");
        }
    }
    @MockitoBean CourseSearchService search;
    @MockitoBean RagAnswerClient ai;
    @MockitoBean DocumentSourceRepository sources;

    void generation(long course,CountDownLatch entered,CountDownLatch release) {
        when(search.search(eq(course),anyLong(),any())).thenAnswer(i->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new CourseSearchResponse(course,"Q",List.of(new SearchHitResponse(999L,888L,"Document",0,"Atomicity",1,1,.9)));
        });
        when(ai.answer(anyString(),anyList())).thenAnswer(i->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown(); assertTrue(release.await(20,TimeUnit.SECONDS));
            return new RagAnswerClient.RagAnswerResult("ANSWERED","Answer: "+i.getArgument(0),"test-model","course-rag-v2",List.of(new RagAnswerClient.Evidence(999L,"Atomicity")));
        });
        when(sources.findPublishedByIds(eq(course),anyLong(),anyList())).thenReturn(List.of(
            new DocumentSourceRepository.PublishedSource(course,888L,"Document",999L,0,"Atomicity",1,1,false)));
    }

    @Test void concurrentRequestsStoreTwoContiguousUserAssistantPairs() throws Exception {
        var f=fixture(); long id=session(f);
        var entered=new CountDownLatch(2); var release=new CountDownLatch(1); generation(f.course(),entered,release);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->call("POST","/api/courses/"+f.course()+"/ai/chat",f.token(),Map.of("question","Q-first","sessionId",id),200));
            var second=pool.submit(()->call("POST","/api/courses/"+f.course()+"/ai/chat",f.token(),Map.of("question","Q-second","sessionId",id),200));
            try { assertTrue(entered.await(15,TimeUnit.SECONDS)); } finally { release.countDown(); }
            first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);
        }
        var messages=call("GET",sessions(f.course())+"/"+id+"/messages",f.token(),null,200).path("content");
        assertEquals(4,messages.size());
        var questions=new HashSet<String>();
        for(int i=0;i<4;i+=2) {
            assertEquals("USER",messages.get(i).path("role").asText());
            assertEquals("ASSISTANT",messages.get(i+1).path("role").asText());
            String question=messages.get(i).path("content").asText();questions.add(question);
            assertEquals("Answer: "+question,messages.get(i+1).path("content").asText());
        }
        assertEquals(Set.of("Q-first","Q-second"),questions);
    }

    @ParameterizedTest @ValueSource(strings={"session","course"})
    void deletionDuringGenerationDoesNotResurrectHistory(String target) throws Exception {
        var f=fixture(); long id=session(f);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);generation(f.course(),entered,release);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var pending=pool.submit(()->send("POST","/api/courses/"+f.course()+"/ai/chat",f.token(),Map.of("question","Q","sessionId",id)));
            try {
                assertTrue(entered.await(15,TimeUnit.SECONDS));
                // This must commit before HTTP generation returns: no long transaction/lock.
                var deletion=pool.submit(()->jdbc.update(target.equals("session")?"DELETE FROM chat_sessions WHERE id=?":"DELETE FROM courses WHERE id=?",target.equals("session")?id:f.course()));
                assertEquals(1,deletion.get(10,TimeUnit.SECONDS));
            } finally { release.countDown(); }
            assertEquals(404,pending.get(20,TimeUnit.SECONDS).statusCode());
        }
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM chat_sessions WHERE id=?",Integer.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=?",Integer.class,id));
    }
}
