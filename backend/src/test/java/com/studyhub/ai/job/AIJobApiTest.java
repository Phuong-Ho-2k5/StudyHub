package com.studyhub.ai.job;

import static com.studyhub.ai.job.AIJobModels.*;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.studyhub.security.JwtService;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.security.core.userdetails.UserDetailsService;

class AIJobApiTest extends AIJobTestSupport {
    @Autowired AIJobService service;
    @Autowired JwtService jwt;
    @Autowired UserDetailsService users;
    @Value("${local.server.port}") int port;
    final ObjectMapper json=new ObjectMapper();
    final HttpClient http=HttpClient.newHttpClient();
    String token(Fixture f) {
        return jwt.generateToken(users.loadUserByUsername(jdbc.queryForObject("select email from users where id=?",String.class,f.user())));
    }
    HttpResponse<String> send(String path,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));
        if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode get(UUID id,String token,int status) throws Exception {
        var response=send("/api/ai/jobs/"+id,token);assertEquals(status,response.statusCode(),response.body());return json.readTree(response.body());
    }
    @Test void allStatesExposeOnlyPublicFieldsAndGetDoesNotMutate() throws Exception {
        var f=fixture();var id=service.enqueue(new CreateAIJob(AIJobType.INGEST_DOCUMENT,f.course(),f.document()),f.user());var token=token(f);
        var before=service.get(id,f.user());var pending=get(id,token,200);assertEquals(before,service.get(id,f.user()));
        assertEquals("PENDING",pending.path("status").asText());assertTrue(pending.path("result").isNull());assertTrue(pending.path("errorCode").isNull());
        assertTrue(pending.path("startedAt").isNull());assertTrue(pending.path("finishedAt").isNull());
        var fields=new HashSet<String>();pending.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("id","type","status","courseId","documentId","createdAt","updatedAt","startedAt","finishedAt","result","errorCode"),fields);
        Instant.parse(pending.path("createdAt").asText());assertEquals(f.course(),pending.path("courseId").asLong());
        var claim=service.claim(id).orElseThrow();var processing=get(id,token,200);
        assertEquals("PROCESSING",processing.path("status").asText());assertTrue(processing.path("result").isNull());assertTrue(processing.path("errorCode").isNull());
        assertFalse(processing.toString().contains(claim.token().toString()));
        service.complete(claim,new AIJobResult(AIJobResultType.DOCUMENT_INDEX,"index-123"));var completed=get(id,token,200);
        assertEquals("COMPLETED",completed.path("status").asText());assertEquals("index-123",completed.at("/result/id").asText());assertEquals("DOCUMENT_INDEX",completed.at("/result/type").asText());assertTrue(completed.path("errorCode").isNull());
        var failedId=service.enqueue(new CreateAIJob(AIJobType.GENERATE_QUIZ,f.course(),null),f.user());service.fail(service.claim(failedId).orElseThrow(),AIJobErrorCode.AI_UNAVAILABLE);
        var failed=get(failedId,token,200);assertEquals("FAILED",failed.path("status").asText());assertTrue(failed.path("result").isNull());assertEquals("AI_UNAVAILABLE",failed.path("errorCode").asText());
    }
    @Test void authenticationAndOwnershipCannotBeBypassed() throws Exception {
        var f=fixture();var other=fixture();var id=service.enqueue(new CreateAIJob(AIJobType.GENERATE_QUIZ,f.course(),null),f.user());
        jdbc.update("update users set role='ADMIN' where id=?",other.user());
        get(id,null,401);get(id,"invalid",401);get(id,token(other),404);get(UUID.randomUUID(),token(f),404);
        assertEquals(400,send("/api/ai/jobs/not-a-uuid",token(f)).statusCode());
        jdbc.update("update workspaces set owner_id=? where id=?",other.user(),f.workspace());get(id,token(f),404);get(id,token(other),404);
    }
    @Test void openApiDescribesReadOnlyJwtContract() throws Exception {
        var spec=json.readTree(send("/v3/api-docs",null).body());var path=spec.path("paths").path("/api/ai/jobs/{jobId}");
        assertTrue(path.has("get"));assertEquals(1,path.size());var op=path.path("get");
        assertEquals("bearerAuth",op.path("security").get(0).fieldNames().next());
        for(String status:List.of("200","400","401","404"))assertTrue(op.path("responses").has(status));
        assertTrue(op.path("responses").path("200").toString().contains("AIJobResponse"));
    }
}
