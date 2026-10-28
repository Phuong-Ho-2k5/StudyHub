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
    String inputType;
    String upgrade;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/embeddings", exchange -> {
            authorization = exchange.getRequestHeaders().getFirst("X-AI-Service-Token");
            upgrade=exchange.getRequestHeaders().getFirst("Upgrade");
            texts = new ArrayList<>();
            var body = json.readTree(exchange.getRequestBody());
            inputType = body.path("inputType").asText("passage");
            body.path("texts").forEach(value -> texts.add(value.asText()));
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
        assertEquals("passage", inputType);
        assertNull(upgrade, "Do not negotiate h2c with Uvicorn");
        assertEquals("model@revision", result.model());
        assertEquals(3, result.dimensions());
        assertEquals(List.of(0.0, 1.0, 0.0), result.vectors().get(1));
    }

    @Test void sendsQueryInputTypeAndPreservesModelValidation() {
        response = "{\"model\":\"model@revision\",\"dimensions\":3,\"vectors\":[[1,0,0]]}";
        var client = client();
        var result = client.embedQuery("When to use transactions?");
        assertEquals("query", inputType);
        assertEquals("secret", authorization);
        assertEquals(List.of("When to use transactions?"), texts);
        assertEquals(client.expectedModel(), result.model());
        assertEquals(client.expectedDimensions(), result.dimensions());
        assertEquals(List.of(1., 0., 0.), result.vectors().getFirst());
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "\n"})
    void rejectsBlankQueryBeforeCallingAi(String query) {
        var error = assertThrows(ResponseStatusException.class, () -> client().embedQuery(query));
        assertEquals(400, error.getStatusCode().value());
        assertNull(authorization);
    }

    @Test void rejectsNullAndOversizedQueryBeforeCallingAi() {
        for (String query : Arrays.asList(null, "x".repeat(16385))) {
            var error = assertThrows(ResponseStatusException.class, () -> client().embedQuery(query));
            assertEquals(400, error.getStatusCode().value());
        }
        assertNull(authorization);
    }

    @ParameterizedTest @ValueSource(ints = {401,403,422,500,503,504})
    void mapsQueryErrorsWithoutLeakingResponse(int upstream) {
        status = upstream;
        response = "private upstream stack trace";
        var error = assertThrows(ResponseStatusException.class, () -> client().embedQuery("Question"));
        assertEquals(upstream == 422 ? 422 : upstream == 504 ? 504 :
                (upstream == 401 || upstream == 403 || upstream == 503) ? 503 : 502,
                error.getStatusCode().value());
        assertFalse(error.getReason().contains(response));
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
        var queryError = assertThrows(ResponseStatusException.class, () -> client().embedQuery("Question"));
        assertEquals(502, queryError.getStatusCode().value());
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
