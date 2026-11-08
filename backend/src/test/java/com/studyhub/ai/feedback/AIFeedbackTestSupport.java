package com.studyhub.ai.feedback;

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
    "spring.datasource.url=jdbc:h2:mem:feedback;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "studyhub.ai.token=", "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
abstract class AIFeedbackTestSupport {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json=new ObjectMapper();
    final HttpClient http=HttpClient.newHttpClient();
    record Fixture(String token,long user,long workspace,long course,long session,long userMessage,long assistant) {}
    Fixture fixture() throws Exception {
        String email="feedback-"+UUID.randomUUID()+"@test.local";
        call("POST","/api/auth/register",null,Map.of("email",email,"password","password123","name","Tester"),201);
        String token=call("POST","/api/auth/login",null,Map.of("email",email,"password","password123"),200).path("accessToken").asText();
        long workspace=call("POST","/api/workspaces",token,Map.of("name","Feedback","description","Test"),201).path("id").asLong();
        long course=call("POST","/api/workspaces/"+workspace+"/courses",token,Map.of("name","Course","description","Test"),201).path("id").asLong();
        long user=jdbc.queryForObject("select owner_id from workspaces where id=?",Long.class,workspace);
        long session=call("POST","/api/courses/"+course+"/ai/chat-sessions",token,Map.of("title","Feedback test"),201).path("id").asLong();
        long u=message(session,"USER",null),a=message(session,"ASSISTANT","INSUFFICIENT_CONTEXT");
        return new Fixture(token,user,workspace,course,session,u,a);
    }
    long message(long session,String role,String status) {
        jdbc.update("insert into chat_messages(session_id,role,content,status,prompt_version,citations_json) values (?,?,?,?,?,?)",
            session,role,"Saved content",status,status==null?null:"course-rag-v2","[]");
        return jdbc.queryForObject("select max(id) from chat_messages where session_id=?",Long.class,session);
    }
    String feedback(long id) {return "/api/ai/messages/"+id+"/feedback";}
    String history(Fixture f) {return "/api/courses/"+f.course()+"/ai/chat-sessions/"+f.session()+"/messages";}
    HttpResponse<String> raw(String method,String path,String token,String body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(token!=null)b.header("Authorization","Bearer "+token);
        return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> send(String method,String path,String token,Object body) throws Exception {
        return raw(method,path,token,body==null?null:json.writeValueAsString(body));
    }
    JsonNode call(String method,String path,String token,Object body,int status) throws Exception {
        var r=send(method,path,token,body);assertEquals(status,r.statusCode(),r.body());
        return r.body().isBlank()?json.nullNode():json.readTree(r.body());
    }
    JsonNode save(Fixture f,Object body,int status) throws Exception {return call("POST",feedback(f.assistant()),f.token(),body,status);}
}
