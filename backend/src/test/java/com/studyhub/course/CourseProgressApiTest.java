package com.studyhub.course;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        "spring.datasource.url=jdbc:h2:mem:course_progress_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class CourseProgressApiTest {
    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void averagesAllConceptsIncludingUnlearnedAndExcludesOtherCourses() throws Exception {
        String owner = registerAndLogin();
        long course = createCourse(owner);
        for (int confidence : new int[] {100, 80, 60, 40, 0}) {
            createConcept(owner, course, confidence);
        }
        createConcept(owner, createCourse(owner), 100);

        assertProgress(owner, course, 5, 56.0, 2, 1, 2, 40.0);
    }

    @Test
    void emptyCourseReturnsZeroCountsAndPercentages() throws Exception {
        String owner = registerAndLogin();
        assertProgress(owner, createCourse(owner), 0, 0.0, 0, 0, 0, 0.0);
    }

    @Test
    void classifiesConfidenceAtBothBoundariesRegardlessOfConceptStatus() throws Exception {
        String owner = registerAndLogin();
        long course = createCourse(owner);
        // All fixtures use NEW status, including the mastered concept.
        for (int confidence : new int[] {49, 50, 79, 80}) {
            createConcept(owner, course, confidence);
        }
        assertProgress(owner, course, 4, 64.5, 1, 2, 1, 25.0);
    }

    @Test
    void roundsProgressAndMasteryToTwoDecimalPlaces() throws Exception {
        String owner = registerAndLogin();
        long course = createCourse(owner);
        createConcept(owner, course, 0);
        createConcept(owner, course, 0);
        long concept = createConcept(owner, course, 1);
        assertProgress(owner, course, 3, 0.33, 0, 0, 3, 0.0);

        updateConcept(owner, concept, 100);
        assertProgress(owner, course, 3, 33.33, 1, 0, 2, 33.33);
    }

    @Test
    void recomputesAfterChangesWithoutChangingCourseStatus() throws Exception {
        String owner = registerAndLogin();
        long course = createCourse(owner);
        long concept = createConcept(owner, course, 100);
        assertProgress(owner, course, 1, 100.0, 1, 0, 0, 100.0);
        assertEquals("PLANNED", json(request("GET", "/api/courses/" + course, owner, null), 200)
                .get("status").asText());

        long added = createConcept(owner, course, 0);
        assertProgress(owner, course, 2, 50.0, 1, 0, 1, 50.0);
        updateConcept(owner, concept, 60);
        assertProgress(owner, course, 2, 30.0, 0, 1, 1, 0.0);
        assertEquals(204, request("DELETE", "/api/concepts/" + added, owner, null).statusCode());
        assertProgress(owner, course, 1, 60.0, 0, 1, 0, 0.0);
    }

    @Test
    void rejectsForeignAndMissingCourses() throws Exception {
        String owner = registerAndLogin();
        String other = registerAndLogin();
        long course = createCourse(owner);
        createConcept(owner, course, 100);
        JsonNode foreign = json(request("GET", progressPath(course), other, null), 404);
        assertEquals("NOT_FOUND", foreign.get("code").asText());
        JsonNode missing = json(request("GET", progressPath(Long.MAX_VALUE), owner, null), 404);
        assertEquals("NOT_FOUND", missing.get("code").asText());
    }

    @Test
    void requiresAuthentication() throws Exception {
        String owner = registerAndLogin();
        long course = createCourse(owner);
        JsonNode error = json(request("GET", progressPath(course), null, null), 401);
        assertEquals("UNAUTHORIZED", error.get("code").asText());
    }

    private void assertProgress(String token, long course, int total, double progress,
            int mastered, int developing, int needsReview, double mastery) throws Exception {
        JsonNode body = json(request("GET", progressPath(course), token, null), 200);
        assertEquals(course, body.get("courseId").asLong());
        assertEquals(total, body.get("totalConcepts").asInt());
        assertEquals(progress, body.get("progressPercentage").asDouble(), 0.000001);
        assertEquals(mastered, body.get("masteredConcepts").asInt());
        assertEquals(developing, body.get("developingConcepts").asInt());
        assertEquals(needsReview, body.get("needsReviewConcepts").asInt());
        assertEquals(mastery, body.get("masteryPercentage").asDouble(), 0.000001);
    }

    private String progressPath(long course) {
        return "/api/courses/" + course + "/progress";
    }

    private String registerAndLogin() throws Exception {
        String email = "progress-" + UUID.randomUUID() + "@example.com";
        json(request("POST", "/api/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\",\"name\":\"Test User\"}"), 201);
        return json(request("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}"), 200)
                .get("accessToken").asText();
    }

    private long createCourse(String token) throws Exception {
        long workspace = json(request("POST", "/api/workspaces", token,
                "{\"name\":\"Progress workspace\",\"description\":\"Notes\"}"), 201).get("id").asLong();
        return json(request("POST", "/api/workspaces/" + workspace + "/courses", token,
                "{\"name\":\"Progress course\",\"description\":\"Notes\"}"), 201).get("id").asLong();
    }

    private long createConcept(String token, long course, int confidence) throws Exception {
        return json(request("POST", "/api/courses/" + course + "/concepts", token,
                conceptBody(confidence)), 201).get("id").asLong();
    }

    private void updateConcept(String token, long concept, int confidence) throws Exception {
        json(request("PUT", "/api/concepts/" + concept, token, conceptBody(confidence)), 200);
    }

    private String conceptBody(int confidence) {
        return "{\"name\":\"Concept-" + UUID.randomUUID() + "\",\"description\":\"Notes\",\"confidence\":"
                + confidence + ",\"status\":\"NEW\"}";
    }

    private JsonNode json(HttpResponse<String> response, int expectedStatus) throws Exception {
        assertEquals(expectedStatus, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
