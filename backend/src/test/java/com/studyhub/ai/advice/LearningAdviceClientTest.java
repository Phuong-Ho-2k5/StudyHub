package com.studyhub.ai.advice;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LearningAdviceClientTest {
    @Test void sendsAuthenticatedFactsAndStrictlyValidatesHttpResponses() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var response=new java.util.concurrent.atomic.AtomicReference<>("{\"status\":\"LEARNING_DATA_ONLY\",\"answer\":\"Review\",\"usedLearningEvidenceIds\":[\"concept:1\"],\"evidence\":[],\"model\":\"gpt-4.1-mini\",\"promptVersion\":\"learning-advice-v1\"}");
        var status=new java.util.concurrent.atomic.AtomicInteger(200);
        server.createContext("/internal/advice/generate",exchange->{
            assertEquals("test-token",exchange.getRequestHeaders().getFirst("X-AI-Service-Token"));
            String body=new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(body.contains("concept:1"));assertTrue(body.contains("learningEvidence"));assertFalse(body.contains("score\":999"));
            byte[] bytes=response.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(),bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            var client=new LearningAdviceClient("http://127.0.0.1:"+server.getAddress().getPort(),"test-token",2,"gpt-4.1-mini");
            var facts=List.of(new LearningAdviceModels.LearningFact("concept:1","CONCEPT",Map.of("confidence",20)));
            assertEquals("LEARNING_DATA_ONLY",client.generate("Help",facts,List.of()).status());
            String good=response.get();
            for (String bad:List.of(good.replace("gpt-4.1-mini","foreign-model"),good.replace("learning-advice-v1","other"),
                good.replace("\"Review\"","\" \""),good.replace("\"evidence\":[]","\"evidence\":[{\"chunkId\":true,\"quote\":\"x\"}]"),
                good.replace("\"answer\":\"Review\"","\"answer\":\"Review\",\"answer\":\"again\""),good+" {}",good.replace("\"evidence\":[]","\"evidence\":[],\"unknown\":1"))) {
                response.set(bad);
                assertEquals(502,assertThrows(ResponseStatusException.class,()->client.generate("Help",facts,List.of())).getStatusCode().value());
            }
            response.set("x".repeat(1048577));
            assertEquals(502,assertThrows(ResponseStatusException.class,()->client.generate("Help",facts,List.of())).getStatusCode().value());
            for (var pair:Map.of(429,503,503,503,504,504,500,502,422,502).entrySet()) {
                status.set(pair.getKey());response.set("{}");
                assertEquals(pair.getValue(),assertThrows(ResponseStatusException.class,()->client.generate("Help",facts,List.of())).getStatusCode().value());
            }
            status.set(422);response.set("{\"detail\":{\"code\":\"TOKEN_BUDGET_EXCEEDED\"}}");
            assertEquals(422,assertThrows(ResponseStatusException.class,()->client.generate("Help",facts,List.of())).getStatusCode().value());
        } finally {server.stop(0);}
    }
}
