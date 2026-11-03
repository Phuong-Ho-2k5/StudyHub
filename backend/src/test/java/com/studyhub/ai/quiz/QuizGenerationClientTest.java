package com.studyhub.ai.quiz;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.studyhub.ai.concept.ConceptSuggestionModels.Context;
import static com.studyhub.ai.quiz.QuizGenerationModels.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;

class QuizGenerationClientTest {
    final ObjectMapper json=new ObjectMapper();
    final SourceWindow window=new SourceWindow(1L,List.of(new DocumentCoverage(11L,"version",List.of(1L))),List.of(new Context(1L,11L,"Doc",0,"Atomicity",1,1,false)));
    String valid() throws Exception {
        return json.writeValueAsString(Map.of("model","gpt-4.1-mini","promptVersion",PROMPT_VERSION,"questions",List.of(Map.of("text","Question","options",List.of(Map.of("text","A","correct",true),Map.of("text","B","correct",false),Map.of("text","C","correct",false),Map.of("text","D","correct",false)),"evidence",List.of(Map.of("chunkId",1,"quote","Atomicity"))))));
    }
    @Test void parserRejectsWrongTypesExtraFieldsAndVersions() throws Exception {
        var client=new QuizGenerationClient("http://localhost","test",1,"gpt-4.1-mini");
        assertEquals(1,client.parse(json.readTree(valid())).questions().size());
        for(String body:List.of(valid().replace("\"correct\":true","\"correct\":\"true\""),valid().replace("\"chunkId\":1","\"chunkId\":1.5"),valid().replace("quiz-generation-v1","other"),valid().replace("gpt-4.1-mini","other")))
            assertThrows(ResponseStatusException.class,()->client.parse(json.readTree(body)));
        var extra=(ObjectNode)json.readTree(valid());extra.put("ownerId",1);assertThrows(ResponseStatusException.class,()->client.parse(extra));
    }
    @Test void realHttpRejectsDuplicateKeysTrailingJsonAndLargeBodiesAndMapsErrors() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var response=new java.util.concurrent.atomic.AtomicReference<>(valid());var status=new java.util.concurrent.atomic.AtomicInteger(200);
        server.createContext("/internal/quizzes/generate",exchange->{
            assertEquals("test",exchange.getRequestHeaders().getFirst("X-AI-Service-Token"));
            var request=json.readTree(exchange.getRequestBody());assertFalse(request.path("contexts").get(0).has("managedFile"));
            var bytes=response.get().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status.get(),bytes.length);
            try {exchange.getResponseBody().write(bytes);}finally {exchange.close();}
        });server.start();
        try {
            var client=new QuizGenerationClient("http://127.0.0.1:"+server.getAddress().getPort(),"test",3,"gpt-4.1-mini");
            for(String body:List.of(valid()+" {}",valid().replace("\"model\":", "\"model\":\"wrong\",\"model\":"),"x".repeat(1048577))) {
                response.set(body);var failure=assertThrows(ResponseStatusException.class,()->client.generate(window,1));
                assertEquals(502,failure.getStatusCode().value(),"body length="+body.length()+", reason="+failure.getReason()+", cause="+failure.getCause());
            }
            for(var entry:Map.of(401,503,429,503,503,503,504,504,500,502,422,422).entrySet()) {
                status.set(entry.getKey());response.set("{\"detail\":{\"code\":\"INSUFFICIENT_CONTENT\"}}");
                assertEquals(entry.getValue(),assertThrows(ResponseStatusException.class,()->client.generate(window,1)).getStatusCode().value());
            }
            response.set("{}");assertEquals(502,assertThrows(ResponseStatusException.class,()->client.generate(window,1)).getStatusCode().value());
        }finally {server.stop(0);}
    }
}
