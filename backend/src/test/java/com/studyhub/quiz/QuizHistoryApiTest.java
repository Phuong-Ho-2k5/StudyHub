package com.studyhub.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyhub.course.CourseRepository;
import com.studyhub.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:quiz_history_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class QuizHistoryApiTest {
    private static final String HISTORY = "/api/users/me/quiz-history";
    @Value("${local.server.port}") private int port;
    @Autowired private UserRepository users;
    @Autowired private CourseRepository courses;
    @Autowired private QuizRepository quizzes;
    @Autowired private QuizAttemptRepository attempts;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requiresValidAuthentication() throws Exception {
        for (String token : new String[] {null, "invalid-token"}) {
            JsonNode error = json(request("GET", HISTORY, token, null), 401);
            assertEquals("UNAUTHORIZED", error.get("code").asText());
        }
    }

    @Test
    void emptyHistoryReturnsAnEmptyPage() throws Exception {
        Account owner = account();
        JsonNode page = history(owner, "");
        assertEquals(0, page.get("content").size());
        assertEquals(0, page.get("totalElements").asLong());
        assertEquals(0, page.get("totalPages").asInt());
        assertEquals(10, page.get("size").asInt());
    }

    @Test
    void isolatesUsersAndIncludesRepeatedAttemptsAcrossCourses() throws Exception {
        Account owner = account();
        Quiz firstQuiz = quiz(owner);
        Quiz secondQuiz = quiz(owner);
        long first = attempt(owner, firstQuiz, "2026-01-01T00:00:00Z");
        long second = attempt(owner, firstQuiz, "2026-01-02T00:00:00Z");
        long third = attempt(owner, secondQuiz, "2026-01-03T00:00:00Z");
        Account other = account();
        long otherAttempt = attempt(other, quiz(other), "2026-01-04T00:00:00Z");

        JsonNode page = history(owner, "?userId=" + other.id());
        assertEquals(3, page.get("totalElements").asLong());
        assertIds(page, third, second, first);
        assertIds(history(other, ""), otherAttempt);
        // Older attempts with pending answers retain their saved totals.
        JsonNode legacy = page.get("content").get(0);
        assertEquals(2, legacy.get("gradedCount").asInt());
        assertEquals(1, legacy.get("pendingCount").asInt());
        assertEquals(100.0 / 3, legacy.get("questionPercentage").asDouble());
    }

    @Test
    void paginatesNewestFirstWithIdAsTimestampTieBreaker() throws Exception {
        Account owner = account();
        Quiz quiz = quiz(owner);
        // The oldest attempt has the largest ID: sorting by ID alone is incorrect.
        long first = attempt(owner, quiz, "2026-01-02T00:00:00Z");
        long second = attempt(owner, quiz, "2026-01-02T00:00:00Z");
        long oldest = attempt(owner, quiz, "2026-01-01T00:00:00Z");

        JsonNode page = history(owner, "?page=0&size=2");
        assertIds(page, second, first);
        assertEquals(3, page.get("totalElements").asLong());
        assertEquals(2, page.get("totalPages").asInt());
        assertIds(history(owner, "?page=1&size=2"), oldest);
        JsonNode beyond = history(owner, "?page=2&size=2");
        assertIds(beyond);
        assertEquals(3, beyond.get("totalElements").asLong());
        assertEquals("2026-01-02T00:00:00Z",
                page.get("content").get(0).get("createdAt").asText());
    }

    @Test
    void supportsExplicitSorting() throws Exception {
        Account owner = account();
        Quiz quiz = quiz(owner);
        long first = attempt(owner, quiz, "2026-01-01T00:00:00Z");
        long second = attempt(owner, quiz, "2026-01-02T00:00:00Z");
        assertIds(history(owner, "?sort=createdAt,asc&sort=id,asc"), first, second);
    }

    @Test
    void readsSavedSubmissionResultsAfterQuestionsChange() throws Exception {
        Account owner = account();
        long quizId = quiz(owner).getId();
        JsonNode question = json(request("POST", "/api/quizzes/" + quizId + "/questions",
                owner.token(), """
                {"text":"True?","type":"TRUE_FALSE","options":[
                    {"text":"True","correct":true},{"text":"False","correct":false}]}
                """), 201);
        long shortAnswer = json(request("POST", "/api/quizzes/" + quizId + "/questions",
                owner.token(), """
                {"text":"Keyword? Enter one word.","type":"SHORT_ANSWER","referenceAnswer":"Reason","options":[]}
                """), 201).get("id").asLong();
        json(request("PUT", "/api/quizzes/" + quizId, owner.token(),
                "{\"title\":\"History quiz\",\"status\":\"PUBLISHED\"}"), 200);
        String submission = "{\"answers\":[{\"questionId\":" + question.get("id").asLong()
                + ",\"answerOptionIds\":[" + question.get("options").get(0).get("id").asLong()
                + "]},{\"questionId\":" + shortAnswer + ",\"answerText\":\"Reason\"}]}";
        json(request("POST", "/api/quizzes/" + quizId + "/submit", owner.token(), submission), 200);
        json(request("PUT", "/api/questions/" + shortAnswer, owner.token(), """
                {"text":"Changed keyword?","type":"SHORT_ANSWER","referenceAnswer":"Changed","options":[]}
                """), 200);
        json(request("POST", "/api/quizzes/" + quizId + "/submit", owner.token(), submission), 200);
        json(request("DELETE", "/api/questions/" + question.get("id").asLong(), owner.token(), null), 204);

        JsonNode page = history(owner, "");
        assertEquals(2, page.get("totalElements").asLong());
        JsonNode newer = page.get("content").get(0);
        assertEquals(2, newer.get("gradedCount").asInt());
        assertEquals(1, newer.get("correctCount").asInt());
        assertEquals(1, newer.get("incorrectCount").asInt());
        assertEquals(0, newer.get("pendingCount").asInt());
        assertEquals(50.0, newer.get("questionPercentage").asDouble());
        JsonNode result = page.get("content").get(1);
        assertEquals(9, result.size());
        assertEquals(quizId, result.get("quizId").asLong());
        assertEquals(2, result.get("totalQuestions").asInt());
        assertEquals(2, result.get("gradedCount").asInt());
        assertEquals(2, result.get("correctCount").asInt());
        assertEquals(0, result.get("incorrectCount").asInt());
        assertEquals(0, result.get("pendingCount").asInt());
        assertEquals(100.0, result.get("questionPercentage").asDouble());
        Instant.parse(result.get("createdAt").asText());
        assertFalse(result.has("user"));
        assertFalse(result.has("questionResults"));
    }

    private JsonNode history(Account account, String query) throws Exception {
        return json(request("GET", HISTORY + query, account.token(), null), 200);
    }

    private void assertIds(JsonNode page, long... ids) {
        JsonNode content = page.get("content");
        assertEquals(ids.length, content.size());
        for (int i = 0; i < ids.length; i++) {
            assertEquals(ids[i], content.get(i).get("attemptId").asLong());
        }
    }

    private long attempt(Account owner, Quiz quiz, String timestamp) {
        long id = attempts.save(new QuizAttempt(quiz, users.findById(owner.id()).orElseThrow(),
                3, 2, 1, 1, 1, 100.0 / 3)).getId();
        jdbc.update("update quiz_attempts set created_at = ? where id = ?",
                Timestamp.from(Instant.parse(timestamp)), id);
        return id;
    }

    private Quiz quiz(Account owner) throws Exception {
        long workspace = json(request("POST", "/api/workspaces", owner.token(),
                "{\"name\":\"History workspace\",\"description\":\"Notes\"}"), 201).get("id").asLong();
        long course = json(request("POST", "/api/workspaces/" + workspace + "/courses", owner.token(),
                "{\"name\":\"History course\",\"description\":\"Notes\"}"), 201).get("id").asLong();
        return quizzes.save(new Quiz("History quiz", courses.findById(course).orElseThrow()));
    }

    private Account account() throws Exception {
        String email = "history-" + UUID.randomUUID() + "@example.com";
        long id = json(request("POST", "/api/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\",\"name\":\"Test User\"}"),
                201).get("id").asLong();
        String token = json(request("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}"),
                200).get("accessToken").asText();
        return new Account(id, token);
    }

    private JsonNode json(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private record Account(long id, String token) {}
}
