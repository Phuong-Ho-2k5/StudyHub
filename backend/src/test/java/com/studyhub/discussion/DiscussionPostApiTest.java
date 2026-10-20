package com.studyhub.discussion;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:discussion_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class DiscussionPostApiTest {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void crudPersistsContentAndUsesAuthenticatedAuthor() throws Exception {
        Account owner = account();
        Account other = account();
        long course = course(owner.token());
        HttpResponse<String> created = request("POST", collection(course), owner.token(), Map.of(
                "title", "  Dependency Injection  ", "content", "  Nội dung\nDòng thứ hai  ",
                "authorId", other.id(), "courseId", Long.MAX_VALUE));
        assertEquals(201, created.statusCode(), created.body());
        JsonNode post = json(created);
        long id = post.path("id").asLong();
        assertEquals(course, post.path("courseId").asLong());
        assertEquals(owner.id(), post.path("authorId").asLong());
        assertEquals("Dependency Injection", post.path("title").asText());
        assertEquals("Nội dung\nDòng thứ hai", post.path("content").asText());
        assertFalse(post.path("createdAt").asText().isBlank());
        assertFalse(post.path("updatedAt").asText().isBlank());
        assertFalse(post.has("author"));
        assertFalse(created.body().contains("passwordHash"));
        assertEquals(post, json(request("GET", item(id), owner.token(), null)));
        var originalCreatedAt = jdbc.queryForObject(
                "SELECT created_at FROM discussion_posts WHERE id = ?", java.sql.Timestamp.class, id);
        jdbc.update("UPDATE discussion_posts SET updated_at = TIMESTAMP '2000-01-01 00:00:00' WHERE id = ?", id);

        HttpResponse<String> updated = request("PUT", item(id), owner.token(), Map.of(
                "title", "  Updated  ", "content", "  New content  ",
                "authorId", other.id(), "courseId", Long.MAX_VALUE));
        assertEquals(200, updated.statusCode(), updated.body());
        JsonNode result = json(updated);
        assertEquals("Updated", result.path("title").asText());
        assertEquals("New content", result.path("content").asText());
        assertEquals(owner.id(), result.path("authorId").asLong());
        assertEquals(course, result.path("courseId").asLong());
        assertEquals(post.path("createdAt"), result.path("createdAt"));
        assertEquals(originalCreatedAt, jdbc.queryForObject(
                "SELECT created_at FROM discussion_posts WHERE id = ?", java.sql.Timestamp.class, id));
        assertEquals(result, json(request("GET", item(id), owner.token(), null)));
        assertTrue(jdbc.queryForObject("SELECT updated_at FROM discussion_posts WHERE id = ?",
                java.sql.Timestamp.class, id).after(java.sql.Timestamp.valueOf("2000-01-01 00:00:00")));
        assertEquals("New content", jdbc.queryForObject(
                "SELECT content FROM discussion_posts WHERE id = ?", String.class, id));

        HttpResponse<String> deleted = request("DELETE", item(id), owner.token(), null);
        assertEquals(204, deleted.statusCode(), deleted.body());
        assertEquals("", deleted.body());
        assertEquals(404, request("GET", item(id), owner.token(), null).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM discussion_posts WHERE id = ?", Integer.class, id));
    }

    @Test
    void listIsCourseScopedAndSupportsPagingAndStableSorting() throws Exception {
        Account owner = account();
        long course = course(owner.token());
        long otherCourse = course(owner.token());
        JsonNode empty = json(request("GET", collection(course), owner.token(), null));
        assertEquals(0, empty.path("totalElements").asInt());
        assertEquals(20, empty.path("size").asInt());
        long first = post(owner.token(), course, "First");
        long second = post(owner.token(), course, "Second");
        post(owner.token(), otherCourse, "Other course");
        jdbc.update("UPDATE discussion_posts SET created_at = TIMESTAMP '2026-01-01 00:00:00' WHERE course_id = ?", course);
        JsonNode page0 = json(request("GET", collection(course) + "?size=1&page=0", owner.token(), null));
        JsonNode page1 = json(request("GET", collection(course) + "?size=1&page=1", owner.token(), null));
        assertEquals(2, page0.path("totalElements").asInt());
        assertEquals(2, page0.path("totalPages").asInt());
        assertEquals(1, page0.path("content").size());
        assertEquals(second, page0.path("content").get(0).path("id").asLong());
        assertEquals(first, page1.path("content").get(0).path("id").asLong());
        JsonNode ascending = json(request("GET", collection(course) + "?sort=id,asc", owner.token(), null));
        assertEquals(first, ascending.path("content").get(0).path("id").asLong());
        JsonNode beyond = json(request("GET", collection(course) + "?size=1&page=5", owner.token(), null));
        assertEquals(0, beyond.path("content").size());
        assertEquals(2, beyond.path("totalElements").asInt());
    }

    @Test
    void foreignCourseAndPostsAreHiddenWithoutChangingData() throws Exception {
        Account owner = account();
        Account other = account();
        long course = course(owner.token());
        long id = post(owner.token(), course, "Private");
        assertNotFound(request("POST", collection(course), other.token(), payload("Stolen", "Content")));
        assertNotFound(request("GET", collection(course), other.token(), null));
        assertNotFound(request("GET", item(id), other.token(), null));
        assertNotFound(request("PUT", item(id), other.token(), payload("Stolen", "Content")));
        assertNotFound(request("DELETE", item(id), other.token(), null));
        assertEquals("Private", json(request("GET", item(id), owner.token(), null)).path("title").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM discussion_posts WHERE course_id = ?", Integer.class, course));
    }

    @Test
    void courseOwnerCannotEditOrDeleteAnotherAuthorsPost() throws Exception {
        Account owner = account();
        Account author = account();
        long course = course(owner.token());
        long id = post(owner.token(), course, "Original");
        // Seed a distinct author to exercise author authorization independently of Course ownership.
        jdbc.update("UPDATE discussion_posts SET author_id = ? WHERE id = ?", author.id(), id);
        assertEquals(200, request("GET", item(id), owner.token(), null).statusCode());
        assertNotFound(request("PUT", item(id), owner.token(), payload("Stolen", "Content")));
        assertNotFound(request("DELETE", item(id), owner.token(), null));
        JsonNode unchanged = json(request("GET", item(id), owner.token(), null));
        assertEquals("Original", unchanged.path("title").asText());
        assertEquals(author.id(), unchanged.path("authorId").asLong());
    }

    @Test
    void validationRejectsMissingBlankAndOversizedFieldsWithoutWriting() throws Exception {
        Account owner = account();
        long course = course(owner.token());
        long id = post(owner.token(), course, "Original");
        Object[] invalid = {
                Map.of(), Map.of("content", "Body"), Map.of("title", "Title"),
                payload("", "Body"), payload(" \t\n", "Body"),
                payload("Title", " \t\n"), payload("x".repeat(201), "Body"),
                payload("Title", "x".repeat(10001))
        };
        for (Object body : invalid) {
            for (String method : new String[]{"POST", "PUT"}) {
                String path = method.equals("POST") ? collection(course) : item(id);
                HttpResponse<String> response = request(method, path, owner.token(), body);
                assertEquals(400, response.statusCode(), response.body());
                assertEquals("VALIDATION_ERROR", json(response).path("code").asText());
                assertTrue(json(response).path("fieldErrors").size() > 0);
            }
        }
        assertEquals("Original", json(request("GET", item(id), owner.token(), null)).path("title").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM discussion_posts WHERE course_id = ?", Integer.class, course));
        Object limits = payload("x".repeat(200), "y".repeat(10000));
        assertEquals(201, request("POST", collection(course), owner.token(), limits).statusCode());
        assertEquals(200, request("PUT", item(id), owner.token(), limits).statusCode());
    }

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        for (String token : new String[]{null, "invalid-token"}) {
            assertEquals(401, request("POST", collection(1), token, payload("Title", "Body")).statusCode());
            assertEquals(401, request("GET", collection(1), token, null).statusCode());
            assertEquals(401, request("GET", item(1), token, null).statusCode());
            assertEquals(401, request("PUT", item(1), token, payload("Title", "Body")).statusCode());
            assertEquals(401, request("DELETE", item(1), token, null).statusCode());
        }
    }

    @Test
    void missingCourseAndPostReturnNotFound() throws Exception {
        Account owner = account();
        assertNotFound(request("POST", collection(Long.MAX_VALUE), owner.token(), payload("Title", "Body")));
        assertNotFound(request("GET", collection(Long.MAX_VALUE), owner.token(), null));
        assertNotFound(request("GET", item(Long.MAX_VALUE), owner.token(), null));
        assertNotFound(request("PUT", item(Long.MAX_VALUE), owner.token(), payload("Title", "Body")));
        assertNotFound(request("DELETE", item(Long.MAX_VALUE), owner.token(), null));
    }

    @Test
    void deletingCourseCascadesToDiscussionPosts() throws Exception {
        Account owner = account();
        long course = course(owner.token());
        long id = post(owner.token(), course, "Cascade");
        assertEquals(204, request("DELETE", "/api/courses/" + course, owner.token(), null).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM discussion_posts WHERE id = ?", Integer.class, id));
        assertNotFound(request("GET", item(id), owner.token(), null));
    }

    @Test
    void deletingAuthorCascadesToPostButPreservesCourse() throws Exception {
        Account owner = account();
        Account author = account();
        long course = course(owner.token());
        long id = post(owner.token(), course, "Author cascade");
        jdbc.update("UPDATE discussion_posts SET author_id = ? WHERE id = ?", author.id(), id);
        jdbc.update("DELETE FROM users WHERE id = ?", author.id());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM discussion_posts WHERE id = ?", Integer.class, id));
        assertEquals(200, request("GET", "/api/courses/" + course, owner.token(), null).statusCode());
        assertNotFound(request("GET", item(id), owner.token(), null));
    }

    private Account account() throws Exception {
        String email = "discussion-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> registered = request("POST", "/api/auth/register", null,
                Map.of("email", email, "password", "Secret123!", "name", "Test User"));
        assertEquals(201, registered.statusCode(), registered.body());
        HttpResponse<String> login = request("POST", "/api/auth/login", null,
                Map.of("email", email, "password", "Secret123!"));
        assertEquals(200, login.statusCode(), login.body());
        return new Account(jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email),
                json(login).path("accessToken").asText());
    }

    private long course(String token) throws Exception {
        HttpResponse<String> workspace = request("POST", "/api/workspaces", token,
                Map.of("name", "Discussion workspace", "description", "Notes"));
        assertEquals(201, workspace.statusCode(), workspace.body());
        HttpResponse<String> course = request("POST", "/api/workspaces/" + json(workspace).path("id").asLong() + "/courses",
                token, Map.of("name", "Discussion course", "description", "Notes"));
        assertEquals(201, course.statusCode(), course.body());
        return json(course).path("id").asLong();
    }

    private long post(String token, long course, String title) throws Exception {
        HttpResponse<String> response = request("POST", collection(course), token, payload(title, "Body"));
        assertEquals(201, response.statusCode(), response.body());
        return json(response).path("id").asLong();
    }

    private HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return mapper.readTree(response.body());
    }

    private void assertNotFound(HttpResponse<String> response) throws Exception {
        assertEquals(404, response.statusCode(), response.body());
        assertEquals("NOT_FOUND", json(response).path("code").asText());
    }

    private Map<String, String> payload(String title, String content) { return Map.of("title", title, "content", content); }
    private String collection(long course) { return "/api/courses/" + course + "/discussion-posts"; }
    private String item(long id) { return "/api/discussion-posts/" + id; }
    private record Account(long id, String token) {}
}
