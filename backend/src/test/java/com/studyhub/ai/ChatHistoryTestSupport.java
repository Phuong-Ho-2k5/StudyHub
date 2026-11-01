package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:chat_history;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "studyhub.ai.token=", "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
abstract class ChatHistoryTestSupport {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();
    final HttpClient http = HttpClient.newHttpClient();

    record Fixture(String token, long owner, long workspace, long course) {}
    Fixture fixture() throws Exception {
        String email="history-"+UUID.randomUUID()+"@example.com";
        call("POST","/api/auth/register",null,Map.of("email",email,"password","password123","name","Tester"),201);
        String token=call("POST","/api/auth/login",null,Map.of("email",email,"password","password123"),200).path("accessToken").asText();
        long workspace=call("POST","/api/workspaces",token,Map.of("name","History","description","Test"),201).path("id").asLong();
        long course=course(token,workspace);
        return new Fixture(token,jdbc.queryForObject("SELECT owner_id FROM workspaces WHERE id=?",Long.class,workspace),workspace,course);
    }
    long course(String token,long workspace) throws Exception {
        return call("POST","/api/workspaces/"+workspace+"/courses",token,Map.of("name","Course-"+UUID.randomUUID(),"description","Test"),201).path("id").asLong();
    }
    String sessions(long course) { return "/api/courses/"+course+"/ai/chat-sessions"; }
    long session(Fixture f) throws Exception {
        return call("POST",sessions(f.course),f.token,Map.of("title","Ôn tập"),201).path("id").asLong();
    }
    HttpResponse<String> send(String method,String path,String token,Object body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(token!=null)builder.header("Authorization","Bearer "+token);
        return http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode call(String method,String path,String token,Object body,int expected) throws Exception {
        var response=send(method,path,token,body);
        assertEquals(expected,response.statusCode(),response.body());
        return response.body().isBlank()?json.nullNode():json.readTree(response.body());
    }
}
