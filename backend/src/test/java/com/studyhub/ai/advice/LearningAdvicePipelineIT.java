package com.studyhub.ai.advice;

import static org.junit.jupiter.api.Assertions.*;
import com.studyhub.document.DocumentEmbeddingClient;
import java.sql.DriverManager;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;

/** Real Spring, PostgreSQL/pgvector, FastAPI and E5; only the external LLM is simulated. */
@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="ADVICE_AI_URL",matches=".+")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class LearningAdvicePipelineIT extends AdviceApiSupport {
    static final String SCHEMA="advice_pipeline_"+UUID.randomUUID().toString().replace("-","");
    static final String MODEL="intfloat/multilingual-e5-small@614241f622f53c4eeff9890bdc4f31cfecc418b3";
    static final String CONTENT="Transaction bảo đảm tính nguyên tử (atomicity).";
    static List<Double> vector;
    static boolean created;

    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) throws Exception {
        try(var connection=database();var statement=connection.createStatement()) {statement.execute("CREATE SCHEMA "+SCHEMA);created=true;}
        String url=System.getenv("PGVECTOR_TEST_URL");
        registry.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+SCHEMA+",public");
        registry.add("spring.datasource.username",()->System.getenv("PGVECTOR_TEST_USER"));
        registry.add("spring.datasource.password",()->System.getenv("PGVECTOR_TEST_PASSWORD"));
        registry.add("spring.flyway.default-schema",()->SCHEMA);
        registry.add("studyhub.ai.url",()->System.getenv("ADVICE_AI_URL"));
        registry.add("studyhub.ai.token",()->"simulation-token");
    }
    static java.sql.Connection database() throws Exception {
        return DriverManager.getConnection(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));
    }
    @AfterAll static void cleanup() throws Exception {
        if(created) try(var connection=database();var statement=connection.createStatement()) {statement.execute("DROP SCHEMA "+SCHEMA+" CASCADE");}
    }
    @BeforeAll static void embedding() {
        vector=new DocumentEmbeddingClient(System.getenv("ADVICE_AI_URL"),"simulation-token",180,MODEL,384).embedQuery(CONTENT).vectors().getFirst();
    }

    String path(Owner owner) {return "/api/courses/"+owner.courseId()+"/ai/learning-advice";}
    long source(Owner owner) throws Exception {
        var response=call("POST","/api/courses/"+owner.courseId()+"/documents",owner.token(),Map.of(
            "title","Database","fileName","test.pdf","fileType","application/pdf","storagePath","legacy/test.pdf"));
        assertEquals(201,response.statusCode(),response.body());
        long document=body(response).path("id").asLong();String version=UUID.randomUUID().toString();
        jdbc.update("insert into document_ingestions(id,document_id,requested_by,status,embedding_model,embedding_dimensions,chunk_count) values (?,?,?,'INDEXED',?,384,1)",version,document,owner.userId(),MODEL);
        jdbc.update("update documents set published_ingestion_id=?,ingestion_status='INDEXED' where id=?",version,document);
        jdbc.update("""
            insert into document_chunks(document_id,ingestion_id,chunk_index,content,page_start,page_end,
              embedding,embedding_model,embedding_dimensions,embedding_text,metadata)
            values (?,?,0,?,1,1,?::vector,?,384,?,'{}'::jsonb)
            """,document,version,CONTENT,vector.toString(),MODEL,CONTENT);
        return document;
    }

    @Test void fullPipelineReturnsVerifiedFactsSourcesAndDoesNotMutateLearningData() throws Exception {
        var owner=owner();long concept=concept(owner,"Atomicity",20);attempt(owner,owner.userId(),20);source(owner);
        jdbc.update("insert into study_sessions(user_id,course_id,start_time,end_time,duration_seconds) values (?,?,CURRENT_TIMESTAMP - INTERVAL '120 seconds',CURRENT_TIMESTAMP,120)",owner.userId(),owner.courseId());
        var response=call("POST",path(owner),owner.token(),Map.of("question","Tôi nên ôn gì?"));
        assertEquals(200,response.statusCode(),response.body());var result=body(response);
        assertEquals("ANSWERED",result.path("status").asText());
        assertEquals("learning-advice-v1",result.path("promptVersion").asText());
        assertEquals("concept:"+concept,result.at("/learningEvidence/0/id").asText());
        assertEquals(20,result.at("/learningEvidence/0/data/confidence").asInt());
        assertEquals(CONTENT,result.at("/citations/0/quote").asText());
        String source=result.at("/citations/0/sourceUrl").asText();
        assertEquals(CONTENT,body(call("GET",source,owner.token(),null)).path("content").asText());
        assertEquals(20,jdbc.queryForObject("select confidence from concepts where id=?",Integer.class,concept));
        assertEquals(1,jdbc.queryForObject("select count(*) from quiz_attempts where user_id=?",Integer.class,owner.userId()));
        assertEquals(0,jdbc.queryForObject("select count(*) from chat_messages",Integer.class));
        var foreign=owner();
        assertEquals(404,call("POST",path(owner),foreign.token(),Map.of("question","Help")).statusCode());
        assertEquals(404,call("GET",source,foreign.token(),null).statusCode());
        jdbc.update("update concepts set confidence=90 where id=?",concept);
        var updated=body(call("POST",path(owner),owner.token(),Map.of("question","Tôi nên ôn gì?")));
        assertEquals(90,updated.at("/learningEvidence/0/data/confidence").asInt());
        assertNotEquals(result.path("answer"),updated.path("answer"));
    }

    @Test void emptyRetrievalColdStartAndUpstreamFailuresHaveDistinctOutcomes() throws Exception {
        var owner=owner();
        var cold=call("POST",path(owner),owner.token(),Map.of("question","Help"));
        assertEquals(200,cold.statusCode());assertTrue(body(cold).path("model").isNull());
        concept(owner,"Atomicity",20);
        var learning=call("POST",path(owner),owner.token(),Map.of("question","Help"));
        assertEquals(200,learning.statusCode(),learning.body());
        assertEquals("LEARNING_DATA_ONLY",body(learning).path("status").asText());
        source(owner);
        for(var pair:Map.of("budget",422,"malformed",502,"unknown-fact",502,"fake-id",502,"fake-quote",502,"rate-limit",503,"unavailable",503,"timeout",504).entrySet()) {
            var response=call("POST",path(owner),owner.token(),Map.of("question","["+pair.getKey()+"] Help"));
            assertEquals(pair.getValue(),response.statusCode(),response.body());assertFalse(response.body().contains("simulation-key"));
        }
    }

    @Test void collectionChecksPublicContractOnDisposableFixtures() throws Exception {
        if(!"1".equals(System.getenv("RUN_ADVICE_COLLECTION"))) return;
        var owner=owner();concept(owner,"Atomicity",20);source(owner);
        var cold=owner();var foreign=owner();var learning=owner();concept(learning,"Atomicity",40);
        var values=Map.of("baseUrl","http://127.0.0.1:"+port,"accessToken",owner.token(),"courseId",Long.toString(owner.courseId()),
            "coldCourseId",Long.toString(cold.courseId()),"coldAccessToken",cold.token(),"foreignCourseId",Long.toString(foreign.courseId()),
            "learningCourseId",Long.toString(learning.courseId()),"learningAccessToken",learning.token());
        var root=Path.of("..").toAbsolutePath().normalize();var env=Files.createTempFile("advice-",".environment.yaml");
        var output=root.resolve("logs/advice-collection.log");
        Files.createDirectories(output.getParent());
        try {
            json.writeValue(env.toFile(),Map.of("name","Disposable advice fixtures","values",values.entrySet().stream().map(e->
                Map.of("key",e.getKey(),"value",e.getValue(),"enabled",true,"type",e.getKey().toLowerCase().contains("token")?"secret":"default")).toList()));
            var process=new ProcessBuilder("postman","collection","run",root.resolve("infra/testing/advice/collection").toString(),"--environment",env.toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();fail("Collection timeout");}
            assertEquals(0,process.exitValue(),"See logs/advice-collection.log");
        }finally {Files.deleteIfExists(env);}
    }
}
