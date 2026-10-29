package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyhub.search.dto.CourseSearchResponse.SearchHitResponse;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class RagAnswerClientTest {
    HttpServer server;
    int status = 200;
    int calls;
    String upgrade;
    String response = """
        {"status":"ANSWERED","answer":"Atomicity","model":"model","promptVersion":"course-rag-v2","evidence":[{"chunkId":1,"quote":"Atomicity"}]}
        """;
    final ObjectMapper json = new ObjectMapper();
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/rag/answer", exchange -> {
            calls++;
            upgrade=exchange.getRequestHeaders().getFirst("Upgrade");
            assertEquals("secret", exchange.getRequestHeaders().getFirst("X-AI-Service-Token"));
            var body = json.readTree(exchange.getRequestBody());
            assertEquals("Question", body.path("question").asText());
            assertEquals(2L, body.path("contexts").get(0).path("documentId").asLong());
            assertFalse(body.path("contexts").get(0).has("score"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    RagAnswerClient client() { return new RagAnswerClient("http://127.0.0.1:" + server.getAddress().getPort(), "secret", 2, "model"); }
    List<SearchHitResponse> hits() { return List.of(new SearchHitResponse(1L,2L,"Database",0,"Atomicity",1,2,.8)); }

    @Test void serializesOnlyAuthorizedContextAndValidatesProfile() {
        assertEquals("Atomicity", client().answer("Question", hits()).answer()); assertEquals(1, calls);
        assertNull(upgrade, "Do not negotiate h2c with Uvicorn");
    }
    @ParameterizedTest @ValueSource(strings={"null", "{}", "<html>private</html>",
            "{\"status\":\"ANSWERED\",\"answer\":\" \",\"model\":\"model\",\"promptVersion\":\"course-rag-v2\"}",
            "{\"status\":\"OTHER\",\"answer\":\"A\",\"model\":\"model\",\"promptVersion\":\"course-rag-v2\"}",
            "{\"status\":\"ANSWERED\",\"answer\":\"A\",\"model\":\"other\",\"promptVersion\":\"course-rag-v2\"}",
            "{\"status\":\"ANSWERED\",\"answer\":\"A\",\"model\":\"model\",\"promptVersion\":\"other\"}"})
    void rejectsInvalidResponses(String body) {
        response = body;
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> client().answer("Question", hits())).getStatusCode().value());
        assertEquals(1, calls);
    }
    @ParameterizedTest @ValueSource(ints={401,403,422,429,500,503,504})
    void mapsErrorsWithoutLeakingOrRetrying(int upstream) {
        status=upstream; response="private-secret";
        var error=assertThrows(ResponseStatusException.class, () -> client().answer("Question", hits()));
        assertEquals(upstream==504 ? 504 : List.of(401,403,429,503).contains(upstream) ? 503 : 502, error.getStatusCode().value());
        assertFalse(error.getReason().contains("private-secret")); assertEquals(1,calls);
    }
    @Test void onlyRecognizedBudgetErrorMapsToUser422() {
        status=422; response="{\"detail\":{\"code\":\"TOKEN_BUDGET_EXCEEDED\"}}";
        assertEquals(422, assertThrows(ResponseStatusException.class, () -> client().answer("Question",hits())).getStatusCode().value());
    }
    @Test void missingConfigDoesNotSendRequest() {
        var c=new RagAnswerClient("", "", 2, "");
        assertEquals(503,assertThrows(ResponseStatusException.class,()->c.answer("Question",hits())).getStatusCode().value());
        assertEquals(0,calls);
    }
    @ParameterizedTest @ValueSource(strings={"null","[]","{}",
        "[{\"chunkId\":\"1\",\"quote\":\"Atomicity\"}]",
        "[{\"chunkId\":1.0,\"quote\":\"Atomicity\"}]",
        "[{\"chunkId\":true,\"quote\":\"Atomicity\"}]",
        "[{\"chunkId\":9223372036854775808,\"quote\":\"Atomicity\"}]",
        "[{\"chunkId\":99,\"quote\":\"Atomicity\"}]",
        "[{\"chunkId\":1,\"quote\":\"fabricated\"}]",
        "[{\"chunkId\":1,\"quote\":\" \"}]",
        "[{\"chunkId\":1,\"quote\":\"Atomicity\",\"url\":\"external\"}]",
        "[{\"chunkId\":1,\"quote\":\"Atomicity\"},{\"chunkId\":1,\"quote\":\"Atomicity\"}]"})
    void rejectsMalformedEvidence(String evidence) throws Exception {
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(response);
        node.set("evidence",json.readTree(evidence));response=node.toString();
        assertEquals(502,assertThrows(ResponseStatusException.class,()->client().answer("Question",hits())).getStatusCode().value());
    }
    @Test void insufficientContextRequiresEmptyEvidence() throws Exception {
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(response);
        node.put("status","INSUFFICIENT_CONTEXT");response=node.toString();
        assertEquals(502,assertThrows(ResponseStatusException.class,()->client().answer("Question",hits())).getStatusCode().value());
        node.putArray("evidence");response=node.toString();assertTrue(client().answer("Question",hits()).evidence().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"\u00a0","\u202f","\u0085"})
    void unicodeWhitespaceOnlyQuoteIsRejected(String quote) throws Exception {
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(response);
        node.putArray("evidence").addObject().put("chunkId",1L).put("quote",quote);response=node.toString();
        var context=List.of(new SearchHitResponse(1L,2L,"DB",0,quote,1,2,.8));
        assertEquals(502,assertThrows(ResponseStatusException.class,()->client().answer("Question",context)).getStatusCode().value());
    }
}
