package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:course_chat_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "studyhub.ai.token=", "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
class CourseChatApiTest {
    @Value("${local.server.port}") int port;
    final ObjectMapper json=new ObjectMapper();
    final HttpClient http=HttpClient.newHttpClient();

    @Test void validatesQuestionAndTopKAndEnforcesOwnershipBeforeAi() throws Exception {
        String owner=login(), other=login();
        long workspace=body(call("POST","/api/workspaces",owner,Map.of("name","Chat","description","Test"))).path("id").asLong();
        long course=body(call("POST","/api/workspaces/"+workspace+"/courses",owner,Map.of("name","DB","description","Test"))).path("id").asLong();
        String path="/api/courses/"+course+"/ai/chat";
        assertEquals(401,call("POST",path,null,Map.of("question","Question")).statusCode());
        assertEquals(404,call("POST",path,other,Map.of("question","Question")).statusCode());
        assertEquals(404,call("POST","/api/courses/9223372036854775807/ai/chat",owner,Map.of("question","Question")).statusCode());
        for (Object invalid:List.of(Map.of(), Map.of("question"," "), Map.of("question","x".repeat(16385)),
                Map.of("question","Question","topK",0),Map.of("question","Question","topK",21))) {
            var r=call("POST",path,owner,invalid);
            assertEquals(400,r.statusCode(),r.body()); assertTrue(body(r).has("fieldErrors"));
        }
        var nullK=new HashMap<String,Object>();nullK.put("question","Question");nullK.put("topK",null);
        assertEquals(400,call("POST",path,owner,nullK).statusCode());
        for (Object invalidType:List.of(Map.of("question",123),Map.of("question",true),
                Map.of("question","Question","topK",1.9),Map.of("question","Question","topK",20.9),
                Map.of("question","Question","topK","5")))
            assertEquals(400,call("POST",path,owner,invalidType).statusCode());
        // Valid input reaches embedding configuration, proving default K is accepted.
        assertEquals(503,call("POST",path,owner,Map.of("question","Question")).statusCode());
    }

    String login() throws Exception {
        String email="chat-"+UUID.randomUUID()+"@example.com";
        var credentials=Map.of("email",email,"password","password123");
        var register=new HashMap<String,Object>(credentials);register.put("name","Chat Tester");
        var r=call("POST","/api/auth/register",null,register);assertEquals(201,r.statusCode(),r.body());
        r=call("POST","/api/auth/login",null,credentials);assertEquals(200,r.statusCode(),r.body());
        return body(r).path("accessToken").asText();
    }
    JsonNode body(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    HttpResponse<String> call(String method,String path,String token,Object body) throws Exception {
        var r=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(token!=null)r.header("Authorization","Bearer "+token);
        return http.send(r.method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
}
