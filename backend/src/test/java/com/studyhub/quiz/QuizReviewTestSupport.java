package com.studyhub.quiz;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:quiz_review;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
abstract class QuizReviewTestSupport {
    static final ObjectMapper JSON = new ObjectMapper();
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    final HttpClient http = HttpClient.newHttpClient();
    record Fixture(String token, long owner, long course, long quiz, List<Long> questions) {
        String path() { return "/api/quizzes/" + quiz; }
    }
    Fixture fixture(int count) throws Exception {
        String email = "review-" + UUID.randomUUID() + "@example.com";
        call("POST", "/api/auth/register", null, Map.of("email",email,"password","password123","name","Reviewer"),201);
        String token = call("POST","/api/auth/login",null,Map.of("email",email,"password","password123"),200).path("accessToken").asText();
        long workspace = call("POST","/api/workspaces",token,Map.of("name","Review","description","Review tests"),201).path("id").asLong();
        long course = call("POST","/api/workspaces/"+workspace+"/courses",token,Map.of("name","Course","description","Review tests"),201).path("id").asLong();
        long quiz = call("POST","/api/courses/"+course+"/quizzes",token,Map.of("title","AI draft"),201).path("id").asLong();
        var questions = new ArrayList<Long>();
        for (int i=0;i<count;i++) questions.add(call("POST","/api/quizzes/"+quiz+"/questions",token,question("Question "+i,null),201).path("id").asLong());
        jdbc.update("UPDATE quizzes SET source_type='AI_GENERATED' WHERE id=?",quiz);
        long owner = jdbc.queryForObject("SELECT owner_id FROM workspaces WHERE id=?",Long.class,workspace);
        return new Fixture(token,owner,course,quiz,questions);
    }
    Map<String,Object> question(String text, Long revision) {
        var body = new LinkedHashMap<String,Object>();
        body.put("text",text); body.put("type","MULTIPLE_CHOICE");
        body.put("options",List.of(Map.of("text","A","correct",true),Map.of("text","B","correct",false),Map.of("text","C","correct",false),Map.of("text","D","correct",false)));
        if(revision!=null) body.put("expectedRevision",revision);
        return body;
    }
    JsonNode action(Fixture f,String action,long revision,int status) throws Exception {
        return call("POST",f.path()+"/"+action,f.token(),Map.of("expectedRevision",revision),status);
    }
    JsonNode reviewAll(Fixture f,long revision) throws Exception {
        JsonNode result = null;
        for(long q:f.questions()) result=action(f,"questions/"+q+"/review",revision,200);
        return result;
    }
    JsonNode get(Fixture f) throws Exception {return call("GET",f.path()+"/review",f.token(),null,200);}
    HttpResponse<String> send(String method,String path,String token,Object body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(token!=null)b.header("Authorization","Bearer "+token);
        return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode call(String method,String path,String token,Object body,int status) throws Exception {
        var response=send(method,path,token,body); assertEquals(status,response.statusCode(),response.body());
        return response.body().isBlank()?JSON.nullNode():JSON.readTree(response.body());
    }
}
