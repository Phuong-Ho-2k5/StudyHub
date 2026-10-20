package com.studyhub.notification;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.studyhub.quiz.QuizSubmittedEvent;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:notification_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class NotificationApiTest {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private PlatformTransactionManager transactionManager;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void successfulSubmissionsCreatePrivateUnreadNotificationsAndCanBeReadRepeatedly() throws Exception {
        Account owner = account();
        Account other = account();
        Fixture fixture = quiz(owner);
        assertEquals(0, count(owner));
        submit(owner, fixture, 200);
        JsonNode page = ok("GET", "/api/notifications", owner);
        assertEquals(1, page.required("totalElements").asInt());
        assertEquals(20, page.required("size").asInt());
        JsonNode notification = page.required("content").get(0);
        long id = notification.required("id").asLong();
        assertTrue(id > 0);
        assertEquals("QUIZ_SUBMITTED", notification.required("type").asText());
        assertTrue(notification.required("content").asText().contains("#" + fixture.quizId()));
        assertTrue(notification.required("content").asText().contains("100"));
        assertFalse(notification.required("read").asBoolean());
        assertFalse(notification.required("createdAt").asText().isBlank());
        assertFalse(notification.has("user"));
        assertFalse(page.toString().contains("passwordHash"));
        assertEquals(1, count(owner));
        assertEquals(0, count(other));
        assertEquals(0, ok("GET", "/api/notifications?userId=" + owner.id(), other)
                .required("totalElements").asInt());
        for (int i = 0; i < 2; i++) {
            JsonNode read = ok("PATCH", "/api/notifications/" + id + "/read", owner);
            assertTrue(read.required("read").asBoolean());
            assertEquals(notification.required("createdAt"), read.required("createdAt"));
            assertEquals(0, count(owner));
        }
        submit(owner, fixture, 200);
        assertEquals(1, count(owner));
        assertEquals(2, ok("GET", "/api/notifications", owner).required("totalElements").asInt());
    }

    @Test
    void foreignAndMissingNotificationsAreHiddenAndCannotBeChanged() throws Exception {
        Account owner = account();
        Account other = account();
        submit(owner, quiz(owner), 200);
        long id = ok("GET", "/api/notifications", owner).required("content").get(0).required("id").asLong();
        for (long target : new long[]{id, Long.MAX_VALUE}) {
            HttpResponse<String> response = request("PATCH", "/api/notifications/" + target + "/read", other.token(), null);
            assertEquals(404, response.statusCode(), response.body());
            assertEquals("NOT_FOUND", json(response).required("code").asText());
        }
        assertEquals(1, count(owner));
    }

    @Test
    void listUsesStableNewestFirstPagingAndHandlesEmptyPages() throws Exception {
        Account owner = account();
        Fixture fixture = quiz(owner);
        assertTrue(ok("GET", "/api/notifications", owner).required("content").isEmpty());
        submit(owner, fixture, 200);
        submit(owner, fixture, 200);
        jdbc.update("UPDATE notifications SET created_at = TIMESTAMP '2026-01-01 00:00:00' WHERE user_id = ?", owner.id());
        List<Long> ids = jdbc.queryForList("SELECT id FROM notifications WHERE user_id = ? ORDER BY id DESC", Long.class, owner.id());
        for (int i = 0; i < 2; i++) {
            JsonNode page = ok("GET", "/api/notifications?size=1&page=" + i, owner);
            assertEquals(2, page.required("totalElements").asInt());
            assertEquals(2, page.required("totalPages").asInt());
            assertEquals(ids.get(i).longValue(), page.required("content").get(0).required("id").asLong());
        }
        JsonNode beyond = ok("GET", "/api/notifications?size=1&page=5", owner);
        assertTrue(beyond.required("content").isEmpty());
        assertEquals(2, beyond.required("totalElements").asInt());
    }

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        for (String token : new String[]{null, "invalid-token"}) {
            assertEquals(401, request("GET", "/api/notifications", token, null).statusCode());
            assertEquals(401, request("GET", "/api/notifications/unread-count", token, null).statusCode());
            assertEquals(401, request("PATCH", "/api/notifications/1/read", token, null).statusCode());
        }
    }

    @Test
    void invalidSubmissionDoesNotCreateAttemptOrNotification() throws Exception {
        Account owner = account();
        Fixture fixture = quiz(owner);
        HttpResponse<String> response = request("POST", "/api/quizzes/" + fixture.quizId() + "/submit",
                owner.token(), Map.of("answers", List.of()));
        assertEquals(400, response.statusCode(), response.body());
        assertEquals(0, count(owner));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempts WHERE quiz_id = ?", Integer.class, fixture.quizId()));
    }

    @Test
    void notificationFailureRollsBackAttemptAndAllowsNextSubmission() throws Exception {
        Account owner = account();
        Fixture fixture = quiz(owner);
        jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT reject_notification CHECK (user_id <> " + owner.id() + ")");
        try {
            submit(owner, fixture, 500);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempts WHERE quiz_id = ?", Integer.class, fixture.quizId()));
            assertEquals(0, count(owner));
            assertEquals(50, jdbc.queryForObject("SELECT confidence FROM concepts WHERE id = ?", Integer.class, fixture.conceptId()));
        } finally {
            jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT reject_notification");
        }
        submit(owner, fixture, 200);
        assertEquals(1, count(owner));
        assertEquals(60, jdbc.queryForObject("SELECT confidence FROM concepts WHERE id = ?", Integer.class, fixture.conceptId()));
    }

    @Test
    void deletingRecipientCascadesNotifications() throws Exception {
        Account owner = account();
        submit(owner, quiz(owner), 200);
        jdbc.update("DELETE FROM users WHERE id = ?", owner.id());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ?", Integer.class, owner.id()));
    }

    @Test
    void laterTransactionFailureRollsBackAlreadyInsertedNotification() throws Exception {
        Account owner = account();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new QuizSubmittedEvent(owner.id(), 12L, 100.0 / 3));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ?", Integer.class, owner.id()));
            assertEquals("Bạn đã hoàn thành quiz #12, đạt 33.33%.", jdbc.queryForObject(
                    "SELECT content FROM notifications WHERE user_id = ?", String.class, owner.id()));
            throw new IllegalStateException("Failure after the notification insert");
        }));
        assertEquals(0, count(owner));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ?", Integer.class, owner.id()));
    }

    private long count(Account owner) throws Exception {
        return ok("GET", "/api/notifications/unread-count", owner).required("unreadCount").asLong();
    }

    private void submit(Account owner, Fixture fixture, int status) throws Exception {
        HttpResponse<String> response = request("POST", "/api/quizzes/" + fixture.quizId() + "/submit", owner.token(),
                Map.of("answers", List.of(Map.of("questionId", fixture.questionId(), "answerOptionIds", List.of(fixture.optionId())))));
        assertEquals(status, response.statusCode(), response.body());
    }

    private Fixture quiz(Account owner) throws Exception {
        long workspace = create("/api/workspaces", owner, Map.of("name", "Workspace", "description", "Notes")).required("id").asLong();
        long course = create("/api/workspaces/" + workspace + "/courses", owner,
                Map.of("name", "Course", "description", "Notes")).required("id").asLong();
        long quiz = create("/api/courses/" + course + "/quizzes", owner, Map.of("title", "Quiz")).required("id").asLong();
        long concept = create("/api/courses/" + course + "/concepts", owner,
                Map.of("name", "Concept", "description", "Notes", "confidence", 50, "status", "LEARNING"))
                .required("id").asLong();
        JsonNode question = create("/api/quizzes/" + quiz + "/questions", owner,
                Map.of("text", "True?", "type", "TRUE_FALSE", "conceptId", concept, "options", List.of(
                        Map.of("text", "True", "correct", true), Map.of("text", "False", "correct", false))));
        HttpResponse<String> published = request("PUT", "/api/quizzes/" + quiz, owner.token(), Map.of("title", "Quiz", "status", "PUBLISHED"));
        assertEquals(200, published.statusCode(), published.body());
        return new Fixture(quiz, question.required("id").asLong(), question.required("options").get(0).required("id").asLong(), concept);
    }

    private Account account() throws Exception {
        String email = "notification-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> registered = request("POST", "/api/auth/register", null,
                Map.of("email", email, "password", "Secret123!", "name", "User"));
        assertEquals(201, registered.statusCode(), registered.body());
        long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        HttpResponse<String> loggedIn = request("POST", "/api/auth/login", null, Map.of("email", email, "password", "Secret123!"));
        assertEquals(200, loggedIn.statusCode(), loggedIn.body());
        return new Account(id, json(loggedIn).required("accessToken").asText());
    }

    private JsonNode create(String path, Account owner, Object body) throws Exception {
        HttpResponse<String> response = request("POST", path, owner.token(), body);
        assertEquals(201, response.statusCode(), response.body());
        return json(response);
    }

    private JsonNode ok(String method, String path, Account owner) throws Exception {
        HttpResponse<String> response = request(method, path, owner.token(), null);
        assertEquals(200, response.statusCode(), response.body());
        return json(response);
    }

    private JsonNode json(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }

    private HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private record Account(long id, String token) {}
    private record Fixture(long quizId, long questionId, long optionId, long conceptId) {}
}
