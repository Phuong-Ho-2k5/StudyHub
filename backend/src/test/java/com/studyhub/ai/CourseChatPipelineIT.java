package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.studyhub.document.*;
import com.studyhub.document.dto.DocumentExtractionResponse.Chunk;
import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

/** Real Spring + PostgreSQL + FastAPI + E5. Only the external OpenAI API is simulated. */
@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="RAG_PIPELINE_AI_URL",matches=".+")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "studyhub.ai.llm-model=gpt-4.1-mini",
    "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
class CourseChatPipelineIT {
    static final String SCHEMA="rag_pipeline_"+UUID.randomUUID().toString().replace("-","");
    static boolean created;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) throws Exception {
        try(var connection=DriverManager.getConnection(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));
                var statement=connection.createStatement()) { statement.execute("CREATE SCHEMA "+SCHEMA);created=true; }
        String url=System.getenv("PGVECTOR_TEST_URL");
        registry.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+SCHEMA+",public");
        registry.add("spring.datasource.username",()->System.getenv("PGVECTOR_TEST_USER"));
        registry.add("spring.datasource.password",()->System.getenv("PGVECTOR_TEST_PASSWORD"));
        registry.add("spring.flyway.default-schema",()->SCHEMA);registry.add("spring.flyway.schemas",()->SCHEMA);
        registry.add("studyhub.ai.url",()->System.getenv("RAG_PIPELINE_AI_URL"));
        registry.add("studyhub.ai.token",()->"simulation-token");
    }
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DocumentEmbeddingClient embeddings;
    @Autowired DocumentChunkRepository chunks;
    @Autowired DocumentIngestionRepository queue;
    final HttpClient http=HttpClient.newHttpClient();
    final ObjectMapper json=new ObjectMapper();

    @Test void realRetrievalAndPromptReachTheExternalProviderSimulation() throws Exception {
        String token=login(), other=login();
        long workspace=call("POST","/api/workspaces",token,Map.of("name","RAG","description","Test"),201).path("id").asLong();
        long course=call("POST","/api/workspaces/"+workspace+"/courses",token,Map.of("name","DB","description","Test"),201).path("id").asLong();
        String path="/api/courses/"+course+"/ai/chat";
        call("POST",path,other,Map.of("question","Foreign owner must not call AI"),404);
        var empty=call("POST",path,token,Map.of("question","Transaction bảo đảm điều gì?"),200);
        assertEquals("INSUFFICIENT_CONTEXT",empty.path("status").asText());assertTrue(empty.path("model").isNull());
        long owner=jdbc.queryForObject("SELECT owner_id FROM workspaces WHERE id=?",Long.class,workspace);
        long document=jdbc.queryForObject("INSERT INTO documents(course_id,title,file_name,file_type,storage_path,managed_file) VALUES (?,'Database','lesson.pdf','application/pdf','pipeline.pdf',true) RETURNING id",Long.class,course);
        String content="Transaction bảo đảm tính nguyên tử (atomicity).";
        var batch=embeddings.embed(List.of(content));
        var chunk=new Chunk(document,"lesson.pdf",0,content,content,List.of(),1,1,List.of(),List.of(),"",List.of(),16,"intfloat/multilingual-e5-small");
        chunks.persist(queue.claimNow(document,owner),new DocumentService.ExtractionSource(document,"lesson.pdf","pipeline.pdf",new byte[]{1}),
                List.of(chunk),batch.vectors(),batch.model(),batch.dimensions());
        var answer=call("POST",path,token,Map.of("question","Transaction bảo đảm điều gì?"),200);
        assertEquals("ANSWERED",answer.path("status").asText());assertTrue(answer.path("answer").asText().contains("atomicity"));
        assertEquals("gpt-4.1-mini",answer.path("model").asText());assertEquals("course-rag-v1",answer.path("promptVersion").asText());
        assertEquals("INSUFFICIENT_CONTEXT",call("POST",path,token,Map.of("question","[insufficient] Ngoài tài liệu"),200).path("status").asText());
        call("POST",path,token,Map.of("question","[budget] Question"),422);
        call("POST",path,token,Map.of("question","[malformed] Question"),502);
        call("POST",path,token,Map.of("question","[rate-limit] Question"),503);
    }

    String login() throws Exception {
        String email="pipeline-"+UUID.randomUUID()+"@example.com";
        call("POST","/api/auth/register",null,Map.of("email",email,"password","Secret123!","name","RAG tester"),201);
        return call("POST","/api/auth/login",null,Map.of("email",email,"password","Secret123!"),200).path("accessToken").asText();
    }
    JsonNode call(String method,String path,String token,Object body,int status) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(token!=null)request.header("Authorization","Bearer "+token);
        var response=http.send(request.method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(status,response.statusCode(),response.body());return json.readTree(response.body());
    }
    @AfterAll static void cleanup() throws Exception {
        if(created)try(var connection=DriverManager.getConnection(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));
                var statement=connection.createStatement()) { statement.execute("DROP SCHEMA "+SCHEMA+" CASCADE"); }
    }
}
