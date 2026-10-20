package com.studyhub.quiz;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:quiz_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class QuizApiTest {
    @Value("${local.server.port}") private int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ownerCanManageQuizWithinCourse() throws Exception {
        String owner = token();
        long courseId = course(owner);
        String collection = "/api/courses/" + courseId + "/quizzes";
        HttpResponse<String> created = send("POST", collection, owner, "{\"title\":\"Linear algebra\"}");
        assertEquals(201, created.statusCode(), created.body());
        JsonNode quiz = json(created);
        long quizId = quiz.path("id").asLong();
        assertTrue(quizId > 0);
        assertEquals(courseId, quiz.path("courseId").asLong());
        assertEquals("DRAFT", quiz.path("status").asText());
        assertEquals("MANUAL", quiz.path("sourceType").asText());

        String item = "/api/quizzes/" + quizId;
        assertEquals("Linear algebra", json(send("GET", item, owner, null)).path("title").asText());
        JsonNode list = json(send("GET", collection, owner, null));
        assertEquals(1, list.size());
        assertEquals(quizId, list.get(0).path("id").asLong());

        HttpResponse<String> updated = send("PUT", item, owner,
                "{\"title\":\"Advanced algebra\",\"status\":\"PUBLISHED\"}");
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals("Advanced algebra", json(updated).path("title").asText());
        assertEquals("PUBLISHED", json(send("GET", item, owner, null)).path("status").asText());
        assertEquals(204, send("DELETE", item, owner, null).statusCode());
        assertEquals(404, send("GET", item, owner, null).statusCode());
        assertEquals(0, json(send("GET", collection, owner, null)).size());
    }

    @Test
    void quizEndpointsEnforceOwnershipAndValidateInput() throws Exception {
        String owner = token();
        String other = token();
        long courseId = course(owner);
        String collection = "/api/courses/" + courseId + "/quizzes";
        assertEquals(401, send("POST", collection, null, "{\"title\":\"Private\"}").statusCode());
        assertEquals(404, send("POST", collection, other, "{\"title\":\"Private\"}").statusCode());
        assertEquals(404, send("GET", collection, other, null).statusCode());
        assertEquals(400, send("POST", collection, owner, "{\"title\":\"  \"}").statusCode());
        assertEquals(400, send("POST", collection, owner,
                "{\"title\":\"" + "x".repeat(201) + "\"}").statusCode());
        HttpResponse<String> created = send("POST", collection, owner, "{\"title\":\"Private\"}");
        assertEquals(201, created.statusCode(), created.body());
        String item = "/api/quizzes/" + json(created).path("id").asLong();
        assertEquals(404, send("GET", item, other, null).statusCode());
        assertEquals(404, send("PUT", item, other,
                "{\"title\":\"Changed\",\"status\":\"PUBLISHED\"}").statusCode());
        assertEquals(404, send("DELETE", item, other, null).statusCode());
        assertEquals(400, send("PUT", item, owner,
                "{\"title\":\"  \",\"status\":\"PUBLISHED\"}").statusCode());
        assertEquals(400, send("PUT", item, owner,
                "{\"title\":\"Changed\",\"status\":\"UNKNOWN\"}").statusCode());
        assertEquals("Private", json(send("GET", item, owner, null)).path("title").asText());
    }

    private String token() throws Exception {
        String email = "quiz-" + UUID.randomUUID() + "@example.com";
        assertEquals(201, send("POST", "/api/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\",\"name\":\"Test User\"}").statusCode());
        return json(send("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}"))
                .path("accessToken").asText();
    }

    private long course(String token) throws Exception {
        long workspaceId = json(send("POST", "/api/workspaces", token,
                "{\"name\":\"Workspace\",\"description\":\"Notes\"}")).path("id").asLong();
        HttpResponse<String> course = send("POST", "/api/workspaces/" + workspaceId + "/courses", token,
                "{\"name\":\"Course\",\"description\":\"Notes\"}");
        assertEquals(201, course.statusCode(), course.body());
        return json(course).path("id").asLong();
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return mapper.readTree(response.body());
    }
}
