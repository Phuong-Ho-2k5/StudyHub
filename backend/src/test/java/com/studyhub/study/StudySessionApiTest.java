package com.studyhub.study;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:study_session_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class StudySessionApiTest {
    @Value("${local.server.port}")
    private int port;
    @Autowired
    private StudySessionRepository repository;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final java.util.Map<String, Long> userIds = new java.util.HashMap<>();

    @Test
    void startsAndFinishesPersistedSessionUsingServerTime() throws Exception {
        String token = registerAndLogin();
        long course = createCourse(token);
        long user = userIds.get(token);
        Instant before = Instant.now().minusSeconds(1);
        JsonNode started = start(token, course);
        long id = started.get("id").asLong();
        Instant start = Instant.parse(started.get("startTime").asText());
        assertEquals(course, started.get("courseId").asLong());
        assertEquals(user, started.get("userId").asLong());
        assertFalse(start.isBefore(before));
        assertFalse(start.isAfter(Instant.now()));
        assertTrue(started.get("endTime").isNull());
        assertTrue(started.get("duration").isNull());
        StudySession stored = repository.findById(id).orElseThrow();
        assertEquals(start, stored.getStartTime());
        assertNull(stored.getEndTime());
        assertNull(stored.getDuration());

        JsonNode finished = json(request("POST", finishPath(id), token, null), 200);
        Instant end = Instant.parse(finished.get("endTime").asText());
        assertEquals(id, finished.get("id").asLong());
        assertEquals(start, Instant.parse(finished.get("startTime").asText()));
        assertFalse(end.isBefore(start));
        assertFalse(end.isAfter(Instant.now()));
        assertEquals(Duration.between(start, end).getSeconds(), finished.get("duration").asLong());
        stored = repository.findById(id).orElseThrow();
        assertEquals(end, stored.getEndTime());
        assertEquals(finished.get("duration").asLong(), stored.getDuration());
    }

    @Test
    void repeatedFinishReturnsConflictWithoutOverwritingResult() throws Exception {
        String token = registerAndLogin();
        long id = start(token, createCourse(token)).get("id").asLong();
        JsonNode finished = json(request("POST", finishPath(id), token, null), 200);
        JsonNode error = json(request("POST", finishPath(id), token, null), 409);
        assertEquals("CONFLICT", error.get("code").asText());
        StudySession stored = repository.findById(id).orElseThrow();
        assertEquals(Instant.parse(finished.get("endTime").asText()), stored.getEndTime());
        assertEquals(finished.get("duration").asLong(), stored.getDuration());
    }

    @Test
    void rejectsMissingAndForeignCoursesWithoutCreatingSessions() throws Exception {
        String owner = registerAndLogin();
        String other = registerAndLogin();
        long course = createCourse(owner);
        long count = repository.count();
        assertEquals(404, request("POST", "/api/study-sessions/start", other, body(course)).statusCode());
        assertEquals(404, request("POST", "/api/study-sessions/start", owner, body(Long.MAX_VALUE)).statusCode());
        assertEquals(count, repository.count());
    }

    @Test
    void rejectsMissingAndForeignSessionsWithoutFinishing() throws Exception {
        String owner = registerAndLogin();
        String other = registerAndLogin();
        long id = start(owner, createCourse(owner)).get("id").asLong();
        assertEquals(404, request("POST", finishPath(id), other, null).statusCode());
        assertEquals(404, request("POST", finishPath(Long.MAX_VALUE), owner, null).statusCode());
        assertNull(repository.findById(id).orElseThrow().getEndTime());
    }

    @Test
    void validatesCourseIdAndRequiresAuthentication() throws Exception {
        String token = registerAndLogin();
        long count = repository.count();
        for (String invalid : List.of("{}", "{\"courseId\":0}", "{\"courseId\":-1}")) {
            JsonNode error = json(request("POST", "/api/study-sessions/start", token, invalid), 400);
            assertEquals("VALIDATION_ERROR", error.get("code").asText());
            assertTrue(error.get("fieldErrors").has("courseId"));
        }
        assertEquals(401, request("POST", "/api/study-sessions/start", null, body(1)).statusCode());
        assertEquals(401, request("POST", finishPath(1), null, null).statusCode());
        assertEquals(count, repository.count());
    }

    @Test
    void concurrentFinishSucceedsOnlyOnce() throws Exception {
        String token = registerAndLogin();
        long id = start(token, createCourse(token)).get("id").asLong();
        CompletableFuture<HttpResponse<String>> first = client.sendAsync(
                buildRequest("POST", finishPath(id), token, null), HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> second = client.sendAsync(
                buildRequest("POST", finishPath(id), token, null), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> a = first.get();
        HttpResponse<String> b = second.get();
        assertEquals(List.of(200, 409), List.of(a.statusCode(), b.statusCode()).stream().sorted().toList());
        JsonNode winner = mapper.readTree(a.statusCode() == 200 ? a.body() : b.body());
        assertEquals(Instant.parse(winner.get("endTime").asText()),
                repository.findById(id).orElseThrow().getEndTime());
    }

    @Test
    void computesExactDurationAcrossDateBoundary() {
        StudySession session = new StudySession(null, null, Instant.parse("2026-10-09T23:59:30Z"));
        session.endSession(Instant.parse("2026-10-10T00:01:00Z"));
        assertEquals(90L, session.getDuration());
    }

    private JsonNode start(String token, long course) throws Exception {
        return json(request("POST", "/api/study-sessions/start", token, body(course)), 201);
    }

    private String body(long course) { return "{\"courseId\":" + course + "}"; }
    private String finishPath(long id) { return "/api/study-sessions/" + id + "/finish"; }

    private String registerAndLogin() throws Exception {
        String email = "session-" + UUID.randomUUID() + "@example.com";
        long userId = json(request("POST", "/api/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\",\"name\":\"Test User\"}"), 201).get("id").asLong();
        String token = json(request("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}"), 200).get("accessToken").asText();
        userIds.put(token, userId);
        return token;
    }

    private long createCourse(String token) throws Exception {
        long workspace = json(request("POST", "/api/workspaces", token,
                "{\"name\":\"Session workspace\",\"description\":\"Notes\"}"), 201).get("id").asLong();
        return json(request("POST", "/api/workspaces/" + workspace + "/courses", token,
                "{\"name\":\"Session course\",\"description\":\"Notes\"}"), 201).get("id").asLong();
    }

    private JsonNode json(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        return client.send(buildRequest(method, path, token, body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest buildRequest(String method, String path, String token, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build();
    }
}
