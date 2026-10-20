package com.studyhub.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyhub.course.CourseRepository;
import com.studyhub.quiz.Quiz;
import com.studyhub.quiz.QuizAttempt;
import com.studyhub.quiz.QuizAttemptRepository;
import com.studyhub.quiz.QuizRepository;
import com.studyhub.study.StudySession;
import com.studyhub.study.StudySessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:user_statistics_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class UserStatisticsApiTest {
    @Value("${local.server.port}") private int port;
    @Autowired private UserRepository users;
    @Autowired private CourseRepository courses;
    @Autowired private StudySessionRepository sessions;
    @Autowired private QuizRepository quizzes;
    @Autowired private QuizAttemptRepository attempts;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void aggregatesAcrossCoursesAndRepeatedAttemptsWithoutMultiplyingRows() throws Exception {
        Account owner = account();
        long firstCourse = course(owner.token());
        long secondCourse = course(owner.token());
        session(owner, firstCourse, 600L);
        session(owner, secondCourse, 1200L);
        session(owner, firstCourse, null);
        Quiz quiz = quiz(firstCourse);
        attempt(owner, quiz, 50.0);
        attempt(owner, quiz, 100.0);
        attempt(owner, quiz(secondCourse), 0.0);

        Account other = account();
        long otherCourse = course(other.token());
        session(other, otherCourse, 9000L);
        attempt(other, quiz(otherCourse), 100.0);

        assertStatistics(owner.token(), 2, 1800, 3, 50.0);
        assertStatistics(other.token(), 1, 9000, 1, 100.0);
        // A caller-supplied ID must never override the authenticated user.
        JsonNode body = json(request("GET", "/api/users/me/statistics?userId=" + other.id(),
                owner.token(), null), 200);
        assertEquals(1800L, body.get("totalStudySeconds").asLong());
    }

    @Test
    void emptyUserAndActiveSessionsReturnZeros() throws Exception {
        Account owner = account();
        assertStatistics(owner.token(), 0, 0, 0, 0.0);
        session(owner, course(owner.token()), null);
        assertStatistics(owner.token(), 0, 0, 0, 0.0);
    }

    @Test
    void roundsAverageAndReadsSavedScoreIncludingPendingQuestions() throws Exception {
        Account owner = account();
        Quiz quiz = quiz(course(owner.token()));
        attempts.save(new QuizAttempt(quiz, users.findById(owner.id()).orElseThrow(),
                3, 2, 1, 1, 1, 100.0 / 3));
        assertStatistics(owner.token(), 0, 0, 1, 33.33);
        attempt(owner, quiz, 100.0);
        assertStatistics(owner.token(), 0, 0, 2, 66.67);
    }

    @Test
    void roundsExactHalfUpRatherThanHalfEven() throws Exception {
        Account owner = account();
        Quiz quiz = quiz(course(owner.token()));
        attempts.save(new QuizAttempt(quiz, users.findById(owner.id()).orElseThrow(),
                32, 32, 1, 31, 0, 3.125));
        assertStatistics(owner.token(), 0, 0, 1, 3.13);
    }

    @Test
    void sumsLongDurationsAndCountsZeroDurationSessions() throws Exception {
        Account owner = account();
        long course = course(owner.token());
        session(owner, course, 2_000_000_000L);
        session(owner, course, 2_000_000_000L);
        session(owner, course, 0L);
        assertStatistics(owner.token(), 3, 4_000_000_000L, 0, 0.0);
    }

    @Test
    void requiresValidAuthentication() throws Exception {
        for (String token : new String[] {null, "invalid-token"}) {
            JsonNode error = json(request("GET", "/api/users/me/statistics", token, null), 401);
            assertEquals("UNAUTHORIZED", error.get("code").asText());
        }
    }

    private void assertStatistics(String token, long completed, long seconds, long count, double average)
            throws Exception {
        JsonNode body = json(request("GET", "/api/users/me/statistics", token, null), 200);
        assertEquals(4, body.size());
        assertEquals(completed, body.get("completedStudySessions").asLong());
        assertEquals(seconds, body.get("totalStudySeconds").asLong());
        assertEquals(count, body.get("quizAttemptCount").asLong());
        assertEquals(average, body.get("averageQuizScore").asDouble(), 0.000001);
    }

    private void session(Account owner, long courseId, Long seconds) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        StudySession session = new StudySession(users.findById(owner.id()).orElseThrow(),
                courses.findById(courseId).orElseThrow(), start);
        if (seconds != null) session.endSession(start.plusSeconds(seconds));
        sessions.save(session);
    }

    private Quiz quiz(long courseId) {
        return quizzes.save(new Quiz("Statistics quiz", courses.findById(courseId).orElseThrow()));
    }

    private void attempt(Account owner, Quiz quiz, double score) {
        int correct = score == 100.0 ? 2 : score == 50.0 ? 1 : 0;
        attempts.save(new QuizAttempt(quiz, users.findById(owner.id()).orElseThrow(),
                2, 2, correct, 2 - correct, 0, score));
    }

    private Account account() throws Exception {
        String email = "statistics-" + UUID.randomUUID() + "@example.com";
        long id = json(request("POST", "/api/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\",\"name\":\"Test User\"}"),
                201).get("id").asLong();
        String token = json(request("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}"),
                200).get("accessToken").asText();
        return new Account(id, token);
    }

    private long course(String token) throws Exception {
        long workspace = json(request("POST", "/api/workspaces", token,
                "{\"name\":\"Statistics workspace\",\"description\":\"Notes\"}"), 201).get("id").asLong();
        return json(request("POST", "/api/workspaces/" + workspace + "/courses", token,
                "{\"name\":\"Statistics course\",\"description\":\"Notes\"}"), 201).get("id").asLong();
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
