package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

/** Real HTTP upload/ingestion + Spring transactions + pgvector; AI runs in Postman simulation. */
@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="AI_SIMULATION_URL",matches=".+")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class DocumentIngestionApiIT {
    static final String SCHEMA="ingestion_api_"+UUID.randomUUID().toString().replace("-", "");
    static final String MODEL="intfloat/multilingual-e5-small@614241f622f53c4eeff9890bdc4f31cfecc418b3";
    static final Path ROOT;
    static { try { ROOT=Files.createTempDirectory("studyhub-ingestion-api-"); } catch (Exception e) { throw new RuntimeException(e); } }
    static boolean schemaCreated;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) throws Exception {
        try (var connection=DriverManager.getConnection(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));
                var statement=connection.createStatement()) {
            statement.execute("CREATE SCHEMA "+SCHEMA); schemaCreated=true;
        }
        String url=System.getenv("PGVECTOR_TEST_URL");
        String scopedUrl=url+(url.contains("?")?"&":"?")+"currentSchema="+SCHEMA+",public";
        registry.add("spring.datasource.url",()->scopedUrl);
        registry.add("spring.datasource.username",()->System.getenv("PGVECTOR_TEST_USER"));
        registry.add("spring.datasource.password",()->System.getenv("PGVECTOR_TEST_PASSWORD"));
        registry.add("spring.flyway.default-schema",()->SCHEMA);
        registry.add("spring.flyway.schemas",()->SCHEMA);
        registry.add("studyhub.storage.root",ROOT::toString);
        registry.add("studyhub.ai.url",()->System.getenv("AI_SIMULATION_URL")+"/success");
        registry.add("studyhub.ai.token",()->"simulation-token");
        registry.add("studyhub.ai.embedding-model",()->MODEL);
        registry.add("studyhub.ai.embedding-dimensions",()->384);
    }
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    final HttpClient http=HttpClient.newHttpClient();
    final ObjectMapper json=new ObjectMapper();

    @Test void ingestsOwnedUploadAtomicallyAndRejectsRepeatWithoutChangingStatus() throws Exception {
        String token=login();
        long workspaceId=call("POST","/api/workspaces",token,Map.of("name","Ingest","description","Test"),201).path("id").asLong();
        long courseId=call("POST","/api/workspaces/"+workspaceId+"/courses",token,Map.of("name","PDF","description","Test"),201).path("id").asLong();
        long documentId=upload(courseId,token);
        String path="/api/documents/"+documentId+"/ingest";
        call("POST",path,null,null,401);
        call("POST",path,login(),null,404);
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id=?",Long.class,documentId));
        var summary=call("POST",path,token,null,200);
        assertEquals(documentId,summary.path("documentId").asLong());
        assertEquals(1,summary.path("chunkCount").asInt());
        assertEquals(MODEL,summary.path("embeddingModel").asText());
        assertEquals(384,summary.path("embeddingDimensions").asInt());
        assertFalse(summary.has("vectors"));
        var row=jdbc.queryForMap("SELECT embedding_text,metadata::text,embedding_model,embedding_dimensions,vector_dims(embedding) AS actual_dimensions FROM document_chunks WHERE document_id=?",documentId);
        assertEquals("StudyHub\nStudyHub extracted text",row.get("embedding_text"));
        assertEquals(MODEL,row.get("embedding_model"));
        assertEquals(384,row.get("actual_dimensions"));
        var metadata=json.readTree((String)row.get("metadata"));
        assertEquals("lesson.pdf",metadata.path("source").asText());
        assertEquals("StudyHub",metadata.path("headings").get(0).asText());
        assertEquals(200,metadata.path("sourceRefs").get(0).path("bbox").path("r").asInt());
        assertEquals(8,metadata.path("tokenCount").asInt());
        assertEquals("intfloat/multilingual-e5-small",metadata.path("tokenizerModel").asText());
        call("POST",path,token,null,409);
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id=?",Long.class,documentId));
        assertEquals("READY",call("GET","/api/documents/"+documentId,token,null,200).path("status").asText());
    }

    String login() throws Exception {
        String email=UUID.randomUUID()+"@example.com";
        call("POST","/api/auth/register",null,Map.of("name","Ingestion","email",email,"password","Secret123!"),201);
        return call("POST","/api/auth/login",null,Map.of("email",email,"password","Secret123!"),200).path("accessToken").asText();
    }
    long upload(long courseId,String token) throws Exception {
        byte[] pdf;
        try (var document=new PDDocument();var output=new ByteArrayOutputStream()) {
            document.addPage(new PDPage());document.save(output);pdf=output.toByteArray();
        }
        String boundary="StudyHub-"+UUID.randomUUID();
        var body=new ByteArrayOutputStream();
        body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\nLesson\r\n--"+boundary+
                "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"lesson.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(pdf);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var response=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/courses/"+courseId+"/documents/upload"))
                .header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(201,response.statusCode(),response.body());return json.readTree(response.body()).path("id").asLong();
    }
    JsonNode call(String method,String path,String token,Object data,int status) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(token!=null)request.header("Authorization","Bearer "+token);
        request.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data)));
        var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(status,response.statusCode(),response.body());return json.readTree(response.body());
    }
    @AfterAll static void cleanup() throws Exception {
        if(schemaCreated)try(var connection=DriverManager.getConnection(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));
                var statement=connection.createStatement()) { statement.execute("DROP SCHEMA "+SCHEMA+" CASCADE"); }
        try(var paths=Files.walk(ROOT)) { for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path); }
    }
}
