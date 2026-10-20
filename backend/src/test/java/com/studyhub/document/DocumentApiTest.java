package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:document_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class DocumentApiTest {
    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void createsDocumentOnlyForCourseOwner() throws Exception {
        String owner = registerAndLogin("document-owner");
        String other = registerAndLogin("document-other");
        long workspaceId = numberField(request("POST", "/api/workspaces", owner,
                "{\"name\":\"Workspace\",\"description\":\"Notes\"}").body(), "id");
        long courseId = numberField(request("POST", "/api/workspaces/" + workspaceId + "/courses", owner,
                "{\"name\":\"Course\",\"description\":\"Notes\"}").body(), "id");
        String path = "/api/courses/" + courseId + "/documents";
        String body = "{\"title\":\"Lecture\",\"fileName\":\"lecture.pdf\",\"fileType\":\"application/pdf\",\"storagePath\":\"docs/lecture.pdf\"}";

        assertEquals(404, request("POST", path, other, body).statusCode());
        assertEquals(401, request("POST", path, null, body).statusCode());
        assertEquals(400, request("POST", path, owner,
                "{\"title\":\"  \",\"fileName\":\"lecture.pdf\",\"fileType\":\"application/pdf\",\"storagePath\":\"docs/lecture.pdf\"}").statusCode());
        assertEquals(400, request("POST", path, owner,
                "{\"title\":\"" + "x".repeat(201) + "\",\"fileName\":\"lecture.pdf\",\"fileType\":\"application/pdf\",\"storagePath\":\"docs/lecture.pdf\"}").statusCode());

        HttpResponse<String> created = request("POST", path, owner, body);
        assertEquals(201, created.statusCode(), created.body());
        assertTrue(numberField(created.body(), "id") > 0);
        assertEquals(courseId, numberField(created.body(), "courseId"));
        assertTrue(created.body().contains("\"title\":\"Lecture\""), created.body());
        assertTrue(created.body().contains("\"status\":\"READY\""), created.body());

        long documentId = numberField(created.body(), "id");
        HttpResponse<String> fetched = request("GET", "/api/documents/" + documentId, owner, null);
        assertEquals(200, fetched.statusCode(), fetched.body());
        assertEquals(courseId, numberField(fetched.body(), "courseId"));

        HttpResponse<String> listed = request("GET", "/api/documents?courseId=" + courseId, owner, null);
        assertEquals(200, listed.statusCode(), listed.body());
        assertEquals(courseId, numberField(listed.body(), "courseId"));
        HttpResponse<String> otherList = request("GET", "/api/documents?courseId=" + courseId, other, null);
        assertEquals(200, otherList.statusCode(), otherList.body());
        assertEquals(0, numberField(otherList.body(), "totalElements"));
    }

    @Test
    void documentCrudRequiresOwningTheCourse() throws Exception {
        String owner = registerAndLogin("document-crud-owner");
        String other = registerAndLogin("document-crud-other");
        long workspaceId = numberField(request("POST", "/api/workspaces", owner,
                "{\"name\":\"Workspace\",\"description\":\"Notes\"}").body(), "id");
        String body = "{\"title\":\"Lecture\",\"fileName\":\"lecture.pdf\",\"fileType\":\"application/pdf\",\"storagePath\":\"docs/lecture.pdf\"}";
        String updatedBody = "{\"title\":\"Updated lecture\",\"fileName\":\"lecture.pdf\",\"fileType\":\"application/pdf\",\"storagePath\":\"docs/lecture.pdf\"}";

        // Two distinct course IDs ensure at least one differs from the owner's user ID.
        for (int i = 0; i < 2; i++) {
            long courseId = numberField(request("POST", "/api/workspaces/" + workspaceId + "/courses", owner,
                    "{\"name\":\"Course " + i + "\",\"description\":\"Notes\"}").body(), "id");
            HttpResponse<String> created = request("POST", "/api/courses/" + courseId + "/documents", owner, body);
            assertEquals(201, created.statusCode(), created.body());
            String path = "/api/documents/" + numberField(created.body(), "id");

            assertEquals(404, request("GET", path, other, null).statusCode());
            assertEquals(404, request("PUT", path, other, updatedBody).statusCode());
            assertEquals(404, request("DELETE", path, other, null).statusCode());

            assertEquals(200, request("GET", path, owner, null).statusCode());
            assertEquals(400, request("PUT", path, owner,
                    "{\"title\":\"\",\"fileName\":\"lecture.pdf\",\"fileType\":\"application/pdf\",\"storagePath\":\"docs/lecture.pdf\"}").statusCode());
            HttpResponse<String> updated = request("PUT", path, owner, updatedBody);
            assertEquals(200, updated.statusCode(), updated.body());
            assertEquals(courseId, numberField(updated.body(), "courseId"));
            assertTrue(updated.body().contains("\"title\":\"Updated lecture\""), updated.body());
            assertEquals(204, request("DELETE", path, owner, null).statusCode());
            assertEquals(404, request("GET", path, owner, null).statusCode());
        }
    }

    private String registerAndLogin(String label) throws Exception {
        String email = label + "-" + UUID.randomUUID() + "@example.com";
        assertEquals(201, request("POST", "/api/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\",\"name\":\"Test User\"}").statusCode());
        HttpResponse<String> login = request("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}");
        assertEquals(200, login.statusCode(), login.body());
        Matcher matcher = Pattern.compile("\\\"accessToken\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(login.body());
        assertTrue(matcher.find(), login.body());
        return matcher.group(1);
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private long numberField(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        assertTrue(matcher.find(), json);
        return Long.parseLong(matcher.group(1));
    }
}
