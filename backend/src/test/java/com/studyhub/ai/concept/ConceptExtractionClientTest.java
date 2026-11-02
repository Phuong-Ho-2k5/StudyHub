package com.studyhub.ai.concept;

import static org.junit.jupiter.api.Assertions.*;
import static com.studyhub.ai.concept.ConceptSuggestionModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ConceptExtractionClientTest {
    final ObjectMapper json=new ObjectMapper();
    final SourceWindow window=new SourceWindow(1L,2L,"version",0,List.of(new Context(3L,2L,"DB",0,"Atomicity",null,null,false)),null);
    String body() throws Exception { return json.writeValueAsString(Map.of("model","gpt-4.1-mini","promptVersion","document-concepts-v1",
        "suggestions",List.of(Map.of("name","Atomicity","description","Atomic transaction","evidence",List.of(Map.of("chunkId",3,"quote","Atomicity")))))); }
    @Test void strictResponseRejectsUnexpectedModelSchemaTypesAndTrailingJson() throws Exception {
        var client=new ConceptExtractionClient("http://localhost","test",1,"gpt-4.1-mini");
        assertEquals("Atomicity",client.parse(json.readTree(body()),5).suggestions().getFirst().name());
        for(String invalid:List.of(body().replace("gpt-4.1-mini","other"),body().replace("document-concepts-v1","other"),
            body().replace("\"chunkId\":3","\"chunkId\":3.5"),body().replace("\"chunkId\":3","\"chunkId\":true"),
            body().replace("\"chunkId\":3","\"chunkId\":9223372036854775808"),body().replace("\"suggestions\":","\"extra\":1,\"suggestions\":")))
            assertEquals(502,assertThrows(ResponseStatusException.class,()->client.parse(json.readTree(invalid),5)).getStatusCode().value());
    }
    @Test void httpErrorsAndOversizedBodiesAreBoundedAndSafe() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var mode=new java.util.concurrent.atomic.AtomicReference<>("success");
        server.createContext("/internal/concepts/extract",exchange->{
            var input=json.readTree(exchange.getRequestBody());
            assertEquals("test",exchange.getRequestHeaders().getFirst("X-AI-Service-Token"));
            assertFalse(input.path("contexts").get(0).has("managedFile"));
            int status=switch(mode.get()) {case "budget"->422;case "unavailable"->503;default->200;};
            String content;
            try { content=switch(mode.get()) { case "budget"->"{\"detail\":{\"code\":\"TOKEN_BUDGET_EXCEEDED\"}}";
                case "oversized"->"x".repeat(1048577);case "trailing"->body()+"{}";default->body();}; }
            catch(Exception error) { throw new java.io.IOException(error); }
            byte[] bytes=content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,0);
            try(var output=exchange.getResponseBody()) { output.write(bytes); } catch(java.io.IOException ignored) {}
        });server.start();
        try {
            var client=new ConceptExtractionClient("http://127.0.0.1:"+server.getAddress().getPort(),"test",2,"gpt-4.1-mini");
            assertEquals(1,client.extract(window,5).suggestions().size());
            for(var entry:Map.of("budget",422,"unavailable",503,"oversized",502,"trailing",502).entrySet()) {
                mode.set(entry.getKey());
                assertEquals(entry.getValue(),assertThrows(ResponseStatusException.class,()->client.extract(window,5)).getStatusCode().value());
            }
        } finally { server.stop(0); }
    }
}
