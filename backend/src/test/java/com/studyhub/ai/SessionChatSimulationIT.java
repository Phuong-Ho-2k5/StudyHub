package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.studyhub.document.*;
import com.studyhub.document.dto.DocumentExtractionResponse.Chunk;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;

/** Real Spring HTTP + PostgreSQL + Java AI clients; only the AI HTTP dependency is simulated. */
@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="AI_SIMULATION_URL",matches=".+")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SessionChatSimulationIT extends ChatHistoryTestSupport {
    static final String SCHEMA="session_http_"+UUID.randomUUID().toString().replace("-","");
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
        registry.add("studyhub.ai.url",()->System.getenv("AI_SIMULATION_URL")+"/success");
        registry.add("studyhub.ai.token",()->"simulation-token");
        registry.add("studyhub.ai.chat-timeout-seconds",()->1);
    }
    @AfterAll static void cleanup() throws Exception {
        try(var connection=DriverManager.getConnection(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));var sql=connection.createStatement()) {
            sql.execute("DROP SCHEMA "+SCHEMA+" CASCADE");
        }
    }
    @Autowired DocumentChunkRepository chunks;
    @Autowired DocumentIngestionRepository queue;
    @Autowired DocumentEmbeddingClient embeddings;

    long document(Fixture f) {
        return jdbc.queryForObject("INSERT INTO documents(course_id,title,file_name,file_type,storage_path,managed_file) VALUES (?,'History source','history.pdf','application/pdf','history-test.pdf',true) RETURNING id",Long.class,f.course());
    }
    void publish(Fixture f,long document,String content) {
        var embedded=embeddings.embed(List.of(content));
        var chunk=new Chunk(document,"history.pdf",0,content,content,List.of(),1,1,List.of(),List.of(),"",List.of(),4,"model");
        chunks.persist(queue.claimNow(document,f.owner()),new DocumentService.ExtractionSource(document,"history.pdf","history-test.pdf",new byte[]{1}),
            List.of(chunk),embedded.vectors(),embedded.model(),embedded.dimensions());
    }
    int count(long session) { return jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=?",Integer.class,session); }

    @Test void persistsRealHttpAnswerAndPreservesCitationsAcrossReingestion() throws Exception {
        var f=fixture();long id=session(f),document=document(f);
        publish(f,document,"Atomicity keeps the transaction whole.");
        String path="/api/courses/"+f.course()+"/ai/chat";
        var first=call("POST",path,f.token(),Map.of("question","Explain atomicity","sessionId",id),200);
        assertEquals("ANSWERED",first.path("status").asText());assertEquals(2,count(id));
        String source=first.path("citations").get(0).path("sourceUrl").asText();
        call("GET",source,f.token(),null,200);
        publish(f,document,"Isolation keeps transactions apart.");
        call("GET",source,f.token(),null,409);
        var history=call("GET",sessions(f.course())+"/"+id+"/messages",f.token(),null,200).path("content");
        assertEquals("Atomicity keeps the transaction whole.",history.get(1).path("content").asText());
        assertEquals("Atomicity keeps the transaction whole.",history.get(1).path("citations").get(0).path("quote").asText());
        var next=call("POST",path,f.token(),Map.of("question","Explain isolation","sessionId",id),200);
        assertEquals("Isolation keeps transactions apart.",next.path("answer").asText());assertEquals(4,count(id));
        var other=fixture();call("GET",sessions(f.course())+"/"+id+"/messages",other.token(),null,404);
    }

    @Test void httpFailuresNeverAppendMessagesAndInsufficientContextIsSaved() throws Exception {
        var f=fixture();long id=session(f),document=document(f);publish(f,document,"Atomicity");
        String path="/api/courses/"+f.course()+"/ai/chat";
        for(var scenario:Map.of("[budget]",422,"[malformed]",502,"[unavailable]",503,"[timeout]",504).entrySet()) {
            call("POST",path,f.token(),Map.of("question",scenario.getKey(),"sessionId",id),scenario.getValue());
            assertEquals(0,count(id));
        }
        call("POST",path,f.token(),Map.of("question","[insufficient]","sessionId",id),200);
        assertEquals(2,count(id));
        var messages=call("GET",sessions(f.course())+"/"+id+"/messages",f.token(),null,200).path("content");
        assertEquals("INSUFFICIENT_CONTEXT",messages.get(1).path("status").asText());
        assertEquals(0,messages.get(1).path("citations").size());
    }
}
