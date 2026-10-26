package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class DocumentEmbeddingClientTest {
    HttpServer server;
    ObjectMapper json = new ObjectMapper();
    String response;
    int status = 200;
    List<String> texts;
    String authorization;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/embeddings", exchange -> {
            authorization = exchange.getRequestHeaders().getFirst("X-AI-Service-Token");
            texts = new ArrayList<>();
            json.readTree(exchange.getRequestBody()).path("texts").forEach(value -> texts.add(value.asText()));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    DocumentEmbeddingClient client() {
        return new DocumentEmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort(), "secret", 2, "model@revision", 3);
    }

    @Test void sendsAuthenticatedTextsAndPreservesOrder() throws Exception {
        response = "{\"model\":\"model@revision\",\"dimensions\":3,\"vectors\":[[1,0,0],[0,1,0]]}";
        var result = client().embed(List.of("Heading\nFirst", "Second"));
        assertEquals("secret", authorization);
        assertEquals(List.of("Heading\nFirst", "Second"), texts);
        assertEquals("model@revision", result.model());
        assertEquals(3, result.dimensions());
        assertEquals(List.of(0.0, 1.0, 0.0), result.vectors().get(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "null", "{}", "{\"model\":\"other\",\"dimensions\":3,\"vectors\":[[1,0,0]]}",
        "{\"model\":\"model@revision\",\"dimensions\":2,\"vectors\":[[1,0]]}",
        "{\"model\":\"model@revision\",\"dimensions\":4294967299,\"vectors\":[[1,0,0]]}",
        "{\"model\":\"model@revision\",\"dimensions\":3,\"vectors\":[]}",
        "{\"model\":\"model@revision\",\"dimensions\":3,\"vectors\":[null]}",
        "{\"model\":\"model@revision\",\"dimensions\":3,\"vectors\":[[1,0]]}",
        "{\"model\":\"model@revision\",\"dimensions\":3,\"vectors\":[[0,0,0]]}",
        "{\"model\":\"model@revision\",\"dimensions\":3,\"vectors\":[[1,null,0]]}",
        "{\"model\":\"model@revision\",\"dimensions\":3,\"vectors\":[[1,\"NaN\",0]]}",
        "{\"model\":\"model@revision\",\"dimensions\":3,\"vectors\":[[1,1e999,0]]}",
        "{\"model\":\"model@revision\",\"dimensions\":3,\"vectors\":[[1,0,0],[0,1,0]]}"
    })
    void rejectsMalformedOrIncompatibleResponse(String body) {
        response = body;
        var error = assertThrows(ResponseStatusException.class, () -> client().embed(List.of("text")));
        assertEquals(502, error.getStatusCode().value());
        assertFalse(error.getReason().contains(body));
    }

    @ParameterizedTest @ValueSource(ints = {401,403,422,500,503,504})
    void mapsUpstreamErrorsWithoutLeakingResponse(int upstream) {
        status = upstream;
        response = "private upstream stack trace";
        var error = assertThrows(ResponseStatusException.class, () -> client().embed(List.of("text")));
        assertEquals(upstream == 504 ? 504 : (upstream == 401 || upstream == 403 || upstream == 503) ? 503 : 502,
                error.getStatusCode().value());
        assertFalse(error.getReason().contains(response));
    }
}
