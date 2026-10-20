package com.studyhub.error;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:error_mvc_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
@Import(ApiErrorMvcTest.FailingController.class)
class ApiErrorMvcTest {
    @Value("${local.server.port}") private int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void authErrorsUseSharedContract() throws Exception {
        String email = "errors-" + UUID.randomUUID() + "@example.com";
        String registration = "{\"name\":\"User\",\"email\":\"" + email + "\",\"password\":\"Secret123!\"}";
        assertEquals(201, send("POST", "/api/auth/register", null, registration).statusCode());
        assertError(send("POST", "/api/auth/register", null, registration), 409, "CONFLICT", "/api/auth/register");
        assertError(send("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Wrong123!\"}"), 401, "UNAUTHORIZED", "/api/auth/login");
    }

    @Test
    void requestAndValidationErrorsUseSharedContract() throws Exception {
        String token = token();
        String path = "/api/workspaces";
        JsonNode validation = assertError(send("POST", path, token, "{\"name\":\"\",\"description\":null}"),
                400, "VALIDATION_ERROR", path);
        assertTrue(validation.path("fieldErrors").has("name"), validation.toString());
        assertTrue(validation.path("fieldErrors").has("description"), validation.toString());
        assertFalse(validation.toString().contains("rejectedValue"));
        assertError(send("POST", path, token, "{"), 400, "INVALID_REQUEST", path);
        assertError(send("GET", "/api/courses?workspaceId=bad", token, null), 400, "INVALID_REQUEST", "/api/courses");
        assertError(send("GET", "/api/concepts?courseId=bad", token, null), 400, "INVALID_REQUEST", "/api/concepts");
        assertError(send("GET", "/api/courses/1/concepts?confidenceBelow=-1", token, null),
                400, "INVALID_REQUEST", "/api/courses/1/concepts");
        assertError(send("GET", "/api/workspaces/not-a-number", token, null), 400, "INVALID_REQUEST", "/api/workspaces/not-a-number");
        assertError(send("POST", "/api/courses/1/concepts", token,
                "{\"name\":\"Bad enum\",\"confidence\":20,\"status\":\"MISSING\"}"),
                400, "INVALID_REQUEST", "/api/courses/1/concepts");
        assertError(send("PUT", path, token, "{}"), 405, "METHOD_NOT_ALLOWED", path);
        assertError(send("GET", "/api/no-such-endpoint", token, null), 404, "NOT_FOUND", "/api/no-such-endpoint");
        HttpRequest unsupported = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("text")).build();
        assertError(client.send(unsupported, HttpResponse.BodyHandlers.ofString()),
                415, "UNSUPPORTED_MEDIA_TYPE", path);
    }

    @Test
    void ownershipErrorsDoNotExposeOtherOwners() throws Exception {
        String owner = token();
        String other = token();
        HttpResponse<String> created = send("POST", "/api/workspaces", owner, "{\"name\":\"Private\",\"description\":\"Notes\"}");
        assertEquals(201, created.statusCode(), created.body());
        long id = mapper.readTree(created.body()).path("id").asLong();
        JsonNode inaccessible = assertError(send("GET", "/api/workspaces/" + id, other, null),
                404, "NOT_FOUND", "/api/workspaces/" + id);
        JsonNode absent = assertError(send("GET", "/api/workspaces/999999999", owner, null),
                404, "NOT_FOUND", "/api/workspaces/999999999");
        assertEquals(absent.path("message"), inaccessible.path("message"));
    }

    @Test
    void unexpectedErrorsHideInternalDetails() throws Exception {
        String path = "/api/test-unexpected-error";
        HttpResponse<String> response = send("GET", path, token(), null);
        JsonNode body = assertError(response, 500, "INTERNAL_ERROR", path);
        assertEquals("Internal server error", body.path("message").asText());
        assertFalse(response.body().contains("private database detail"));
    }

    @Test
    void businessRuleErrorsUseInvalidRequest() throws Exception {
        String owner = token();
        HttpResponse<String> workspace = send("POST", "/api/workspaces", owner,
                "{\"name\":\"Rules\",\"description\":\"Notes\"}");
        assertEquals(201, workspace.statusCode(), workspace.body());
        long workspaceId = mapper.readTree(workspace.body()).path("id").asLong();
        HttpResponse<String> course = send("POST", "/api/workspaces/" + workspaceId + "/courses", owner,
                "{\"name\":\"Rules\",\"description\":\"Notes\"}");
        assertEquals(201, course.statusCode(), course.body());
        long courseId = mapper.readTree(course.body()).path("id").asLong();
        HttpResponse<String> concept = send("POST", "/api/courses/" + courseId + "/concepts", owner,
                "{\"name\":\"Self\",\"confidence\":20,\"status\":\"NEW\"}");
        assertEquals(201, concept.statusCode(), concept.body());
        long conceptId = mapper.readTree(concept.body()).path("id").asLong();
        String path = "/api/concepts/" + conceptId + "/prerequisites";
        JsonNode error = assertError(send("POST", path, owner,
                "{\"prerequisiteId\":" + conceptId + "}"), 400, "INVALID_REQUEST", path);
        assertEquals("A concept cannot be a prerequisite of itself", error.path("message").asText());
    }

    @RestController
    static class FailingController {
        @GetMapping("/api/test-unexpected-error")
        String fail() {
            throw new IllegalStateException("private database detail");
        }
    }

    private String token() throws Exception {
        String email = "errors-" + UUID.randomUUID() + "@example.com";
        assertEquals(201, send("POST", "/api/auth/register", null,
                "{\"name\":\"User\",\"email\":\"" + email + "\",\"password\":\"Secret123!\"}").statusCode());
        HttpResponse<String> login = send("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}");
        assertEquals(200, login.statusCode());
        return mapper.readTree(login.body()).path("accessToken").asText();
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode assertError(HttpResponse<String> response, int status, String code, String path) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"), response.body());
        JsonNode json = mapper.readTree(response.body());
        assertEquals(code, json.path("code").asText(), response.body());
        assertFalse(json.path("message").asText().isBlank(), response.body());
        assertEquals(path, json.path("path").asText(), response.body());
        assertDoesNotThrow(() -> Instant.parse(json.path("timestamp").asText()));
        assertFalse(response.body().contains("stackTrace"));
        return json;
    }
}
