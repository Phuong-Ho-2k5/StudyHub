package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;

abstract class SourceApiSupport {
    @Value("${local.server.port}") int port;
    final ObjectMapper json=new ObjectMapper();
    final HttpClient http=HttpClient.newHttpClient();
    HttpResponse<byte[]> call(String method,String path,String token,Object body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(body!=null)request.header("Content-Type","application/json");
        return http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():
            HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    JsonNode create(String path,String token,Object body) throws Exception {
        var r=call("POST",path,token,body);assertEquals(201,r.statusCode(),new String(r.body()));return json.readTree(r.body());
    }
    String login() throws Exception {
        String email="source-"+UUID.randomUUID()+"@example.com";
        create("/api/auth/register",null,Map.of("email",email,"password","Secret123!","name","Tester"));
        return json.readTree(call("POST","/api/auth/login",null,Map.of("email",email,"password","Secret123!")).body()).path("accessToken").asText();
    }
    long course(String owner) throws Exception {
        long workspace=create("/api/workspaces",owner,Map.of("name","Sources","description","Test")).path("id").asLong();
        return create("/api/workspaces/"+workspace+"/courses",owner,Map.of("name","DB","description","Test")).path("id").asLong();
    }
    long document(long course,String owner) throws Exception {
        return create("/api/courses/"+course+"/documents",owner,Map.of("title","DB","fileName","test.pdf",
            "fileType","application/pdf","storagePath","legacy/test.pdf")).path("id").asLong();
    }
}
