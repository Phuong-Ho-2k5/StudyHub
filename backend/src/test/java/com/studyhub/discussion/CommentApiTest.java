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
        "spring.datasource.url=jdbc:h2:mem:comment_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class CommentApiTest {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void crudUsesAuthenticatedAuthorAndPreservesParentAndCreationTime() throws Exception {
        Account owner = account();
        Account other = account();
        long post = post(owner.token(), course(owner.token()));
        HttpResponse<String> created = request("POST", collection(post), owner.token(), Map.of(
                "content", "  Nội dung\nDòng thứ hai  ", "authorId", other.id(), "postId", Long.MAX_VALUE));
        assertEquals(201, created.statusCode(), created.body());
        JsonNode comment = json(created);
        long id = comment.path("id").asLong();
        assertTrue(id > 0);
        assertEquals(post, comment.path("postId").asLong());
        assertEquals(owner.id(), comment.path("authorId").asLong());
        assertEquals("Nội dung\nDòng thứ hai", comment.path("content").asText());
        assertFalse(comment.path("createdAt").asText().isBlank());
        assertFalse(comment.path("updatedAt").asText().isBlank());
        assertFalse(comment.has("author"));
        assertFalse(comment.has("post"));
        assertFalse(created.body().contains("passwordHash"));
        assertEquals(comment, json(request("GET", item(id), owner.token(), null)));
        jdbc.update("UPDATE comments SET updated_at = TIMESTAMP '2000-01-01 00:00:00' WHERE id = ?", id);

        HttpResponse<String> updated = request("PUT", item(id), owner.token(), Map.of(
                "content", "  Updated  ", "authorId", other.id(), "postId", Long.MAX_VALUE));
        assertEquals(200, updated.statusCode(), updated.body());
        JsonNode result = json(updated);
        assertEquals("Updated", result.path("content").asText());
        assertEquals(post, result.path("postId").asLong());
        assertEquals(owner.id(), result.path("authorId").asLong());
        assertEquals(comment.path("createdAt"), result.path("createdAt"));
        assertTrue(jdbc.queryForObject("SELECT updated_at FROM comments WHERE id = ?",
                java.sql.Timestamp.class, id).after(java.sql.Timestamp.valueOf("2000-01-01 00:00:00")));
        assertEquals("Updated", jdbc.queryForObject("SELECT content FROM comments WHERE id = ?", String.class, id));
        assertEquals(result, json(request("GET", item(id), owner.token(), null)));

        HttpResponse<String> deleted = request("DELETE", item(id), owner.token(), null);
        assertEquals(204, deleted.statusCode(), deleted.body());
        assertEquals("", deleted.body());
        assertNotFound(request("GET", item(id), owner.token(), null));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE id = ?", Integer.class, id));
        assertEquals(200, request("GET", "/api/discussion-posts/" + post, owner.token(), null).statusCode());
    }

    @Test
    void listIsPostScopedWithStableChronologicalPaging() throws Exception {
        Account owner = account();
        long course = course(owner.token());
        long post = post(owner.token(), course);
        long otherPost = post(owner.token(), course);
        JsonNode empty = json(request("GET", collection(post), owner.token(), null));
        assertEquals(0, empty.path("totalElements").asInt());
        assertEquals(20, empty.path("size").asInt());
        long first = comment(owner.token(), post, "First");
        long second = comment(owner.token(), post, "Second");
        comment(owner.token(), otherPost, "Other post");
        jdbc.update("UPDATE comments SET created_at = TIMESTAMP '2026-01-01 00:00:00' WHERE post_id = ?", post);
        JsonNode page0 = json(request("GET", collection(post) + "?size=1&page=0", owner.token(), null));
        JsonNode page1 = json(request("GET", collection(post) + "?size=1&page=1", owner.token(), null));
        assertEquals(2, page0.path("totalElements").asInt());
        assertEquals(2, page0.path("totalPages").asInt());
        assertEquals(1, page0.path("content").size());
        assertEquals(first, page0.path("content").get(0).path("id").asLong());
        assertEquals(second, page1.path("content").get(0).path("id").asLong());
        JsonNode descending = json(request("GET", collection(post) + "?sort=id,desc", owner.token(), null));
        assertEquals(second, descending.path("content").get(0).path("id").asLong());
        JsonNode beyond = json(request("GET", collection(post) + "?size=1&page=5", owner.token(), null));
        assertEquals(0, beyond.path("content").size());
        assertEquals(2, beyond.path("totalElements").asInt());
    }

    @Test
    void foreignCourseIsHiddenAndDeniedWritesPreserveData() throws Exception {
        Account owner = account();
        Account other = account();
        long post = post(owner.token(), course(owner.token()));
        long id = comment(owner.token(), post, "Original");
        assertNotFound(request("POST", collection(post), other.token(), payload("Stolen")));
        assertNotFound(request("GET", collection(post), other.token(), null));
        assertNotFound(request("GET", item(id), other.token(), null));
        assertNotFound(request("PUT", item(id), other.token(), payload("Stolen")));
        assertNotFound(request("DELETE", item(id), other.token(), null));
        // Being the author does not bypass Course access.
        jdbc.update("UPDATE comments SET author_id = ? WHERE id = ?", other.id(), id);
        assertNotFound(request("GET", item(id), other.token(), null));
        assertNotFound(request("PUT", item(id), other.token(), payload("Stolen")));
        assertNotFound(request("DELETE", item(id), other.token(), null));
        assertEquals("Original", json(request("GET", item(id), owner.token(), null)).path("content").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE post_id = ?", Integer.class, post));
    }

    @Test
    void courseOwnerCannotEditOrDeleteAnotherAuthorsComment() throws Exception {
        Account owner = account();
        Account author = account();
        long post = post(owner.token(), course(owner.token()));
        long id = comment(owner.token(), post, "Original");
        jdbc.update("UPDATE comments SET author_id = ? WHERE id = ?", author.id(), id);
        assertEquals(200, request("GET", item(id), owner.token(), null).statusCode());
        assertNotFound(request("PUT", item(id), owner.token(), payload("Stolen")));
        assertNotFound(request("DELETE", item(id), owner.token(), null));
        JsonNode unchanged = json(request("GET", item(id), owner.token(), null));
        assertEquals("Original", unchanged.path("content").asText());
        assertEquals(author.id(), unchanged.path("authorId").asLong());
    }

    @Test
    void validationRejectsMissingBlankAndOversizedContentWithoutWriting() throws Exception {
        Account owner = account();
        long post = post(owner.token(), course(owner.token()));
        long id = comment(owner.token(), post, "Original");
        Object[] invalid = {Map.of(), mapper.readTree("{\"content\":null}"), payload(""),
                payload(" \t\n"), payload("x".repeat(10001))};
        for (Object body : invalid) {
            for (String method : new String[]{"POST", "PUT"}) {
                HttpResponse<String> response = request(method, method.equals("POST") ? collection(post) : item(id),
                        owner.token(), body);
                assertEquals(400, response.statusCode(), response.body());
                assertEquals("VALIDATION_ERROR", json(response).path("code").asText());
                assertTrue(json(response).path("fieldErrors").has("content"));
            }
        }
        assertEquals("Original", json(request("GET", item(id), owner.token(), null)).path("content").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE post_id = ?", Integer.class, post));
        assertEquals(201, request("POST", collection(post), owner.token(), payload("x".repeat(10000))).statusCode());
        assertEquals(200, request("PUT", item(id), owner.token(), payload("x".repeat(10000))).statusCode());
    }

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        for (String token : new String[]{null, "invalid-token"}) {
            assertEquals(401, request("POST", collection(1), token, payload("Body")).statusCode());
            assertEquals(401, request("GET", collection(1), token, null).statusCode());
            assertEquals(401, request("GET", item(1), token, null).statusCode());
            assertEquals(401, request("PUT", item(1), token, payload("Body")).statusCode());
            assertEquals(401, request("DELETE", item(1), token, null).statusCode());
        }
    }

    @Test
    void missingPostAndCommentReturnNotFound() throws Exception {
        Account owner = account();
        assertNotFound(request("POST", collection(Long.MAX_VALUE), owner.token(), payload("Body")));
        assertNotFound(request("GET", collection(Long.MAX_VALUE), owner.token(), null));
        assertNotFound(request("GET", item(Long.MAX_VALUE), owner.token(), null));
        assertNotFound(request("PUT", item(Long.MAX_VALUE), owner.token(), payload("Body")));
        assertNotFound(request("DELETE", item(Long.MAX_VALUE), owner.token(), null));
    }

    @Test
    void deletingPostCascadesOnlyItsComments() throws Exception {
        Account owner = account();
        long course = course(owner.token());
        long post = post(owner.token(), course);
        long otherPost = post(owner.token(), course);
        long id = comment(owner.token(), post, "Cascade");
        long survivor = comment(owner.token(), otherPost, "Keep");
        assertEquals(204, request("DELETE", "/api/discussion-posts/" + post, owner.token(), null).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE id = ?", Integer.class, id));
        assertNotFound(request("GET", item(id), owner.token(), null));
        assertNotFound(request("GET", collection(post), owner.token(), null));
        assertEquals(200, request("GET", item(survivor), owner.token(), null).statusCode());
    }

    @Test
    void deletingCourseCascadesThroughPostsToComments() throws Exception {
        Account owner = account();
        long course = course(owner.token());
        long post = post(owner.token(), course);
        long id = comment(owner.token(), post, "Cascade");
        assertEquals(204, request("DELETE", "/api/courses/" + course, owner.token(), null).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE id = ?", Integer.class, id));
        assertNotFound(request("GET", item(id), owner.token(), null));
    }

    @Test
    void deletingCommentAuthorPreservesPostAndOtherComments() throws Exception {
        Account owner = account();
        Account author = account();
        long post = post(owner.token(), course(owner.token()));
        long id = comment(owner.token(), post, "Author cascade");
        long survivor = comment(owner.token(), post, "Keep");
        jdbc.update("UPDATE comments SET author_id = ? WHERE id = ?", author.id(), id);
        jdbc.update("DELETE FROM users WHERE id = ?", author.id());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE id = ?", Integer.class, id));
        assertNotFound(request("GET", item(id), owner.token(), null));
        assertEquals(200, request("GET", "/api/discussion-posts/" + post, owner.token(), null).statusCode());
        assertEquals(200, request("GET", item(survivor), owner.token(), null).statusCode());
    }

    private Account account() throws Exception {
        String email = "comment-" + UUID.randomUUID() + "@example.com";
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
                Map.of("name", "Comment workspace", "description", "Notes"));
        assertEquals(201, workspace.statusCode(), workspace.body());
        HttpResponse<String> course = request("POST", "/api/workspaces/" + json(workspace).path("id").asLong() + "/courses",
                token, Map.of("name", "Comment course", "description", "Notes"));
        assertEquals(201, course.statusCode(), course.body());
        return json(course).path("id").asLong();
    }

    private long post(String token, long course) throws Exception {
        HttpResponse<String> response = request("POST", "/api/courses/" + course + "/discussion-posts", token,
                Map.of("title", "Discussion", "content", "Body"));
        assertEquals(201, response.statusCode(), response.body());
        return json(response).path("id").asLong();
    }

    private long comment(String token, long post, String content) throws Exception {
        HttpResponse<String> response = request("POST", collection(post), token, payload(content));
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

    private JsonNode json(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }
    private void assertNotFound(HttpResponse<String> response) throws Exception {
        assertEquals(404, response.statusCode(), response.body());
        assertEquals("NOT_FOUND", json(response).path("code").asText());
    }
    private Map<String, String> payload(String content) { return Map.of("content", content); }
    private String collection(long post) { return "/api/discussion-posts/" + post + "/comments"; }
    private String item(long id) { return "/api/comments/" + id; }
    private record Account(long id, String token) {}
}
