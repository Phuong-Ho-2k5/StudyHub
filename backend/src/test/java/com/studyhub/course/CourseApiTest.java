package com.studyhub.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        "spring.datasource.url=jdbc:h2:mem:course_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class CourseApiTest {
    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void courseCrudIsRestrictedToWorkspaceOwner() throws Exception {
        String owner = registerAndLogin("owner");
        String other = registerAndLogin("other");
        long workspaceId = createWorkspace(owner, "Private workspace");

        assertEquals(404, request("POST", "/api/workspaces/" + workspaceId + "/courses", other,
                "{\"name\":\"Stolen\",\"description\":\"Notes\"}").statusCode());

        HttpResponse<String> created = request("POST", "/api/workspaces/" + workspaceId + "/courses", owner,
                "{\"name\":\"Algebra\",\"description\":\"Notes\"}");
        assertEquals(201, created.statusCode(), created.body());
        long courseId = numberField(created.body(), "id");
        assertTrue(created.body().contains("\"status\":\"PLANNED\""));

        String path = "/api/courses/" + courseId;
        assertEquals(200, request("GET", path, owner, null).statusCode());
        assertEquals(404, request("GET", path, other, null).statusCode());
        assertEquals(404, request("PUT", path, other,
                "{\"name\":\"Stolen\",\"description\":\"Notes\",\"status\":\"COMPLETED\"}").statusCode());
        assertEquals(404, request("DELETE", path, other, null).statusCode());

        HttpResponse<String> updated = request("PUT", path, owner,
                "{\"name\":\"Advanced Algebra\",\"description\":\"Notes\",\"status\":\"COMPLETED\"}");
        assertEquals(200, updated.statusCode(), updated.body());
        assertTrue(updated.body().contains("\"name\":\"Advanced Algebra\""));
        assertTrue(updated.body().contains("\"status\":\"COMPLETED\""));
        assertEquals(204, request("DELETE", path, owner, null).statusCode());
        assertEquals(404, request("GET", path, owner, null).statusCode());
    }

    @Test
    void listCombinesFiltersWithPagingSortingAndOwnerScope() throws Exception {
        String owner = registerAndLogin("list-owner");
        String other = registerAndLogin("list-other");
        long firstWorkspace = createWorkspace(owner, "First workspace");
        long secondWorkspace = createWorkspace(owner, "Second workspace");
        long foreignWorkspace = createWorkspace(other, "Foreign workspace");

        createCourse(owner, firstWorkspace, "Java Basics");
        createCourse(owner, firstWorkspace, "Java Intermediate");
        long completedId = createCourse(owner, firstWorkspace, "Java Advanced");
        createCourse(owner, firstWorkspace, "History");
        createCourse(owner, secondWorkspace, "Java Web");
        createCourse(other, foreignWorkspace, "Java Secret");
        assertEquals(200, request("PUT", "/api/courses/" + completedId, owner,
                "{\"name\":\"Java Advanced\",\"description\":\"Notes\",\"status\":\"COMPLETED\"}").statusCode());

        String filter = "/api/courses?workspaceId=" + firstWorkspace
                + "&status=PLANNED&q=java&size=1&sort=name,asc&page=";
        HttpResponse<String> firstPage = request("GET", filter + "0", owner, null);
        HttpResponse<String> secondPage = request("GET", filter + "1", owner, null);
        assertEquals(200, firstPage.statusCode(), firstPage.body());
        assertEquals(200, secondPage.statusCode(), secondPage.body());
        assertTrue(firstPage.body().contains("\"totalElements\":2"), firstPage.body());
        assertTrue(firstPage.body().contains("Java Basics"), firstPage.body());
        assertFalse(firstPage.body().contains("Java Intermediate"), firstPage.body());
        assertTrue(secondPage.body().contains("Java Intermediate"), secondPage.body());
        assertFalse(secondPage.body().contains("Java Basics"), secondPage.body());

        HttpResponse<String> completed = request("GET", "/api/courses?status=COMPLETED&q=java", owner, null);
        assertEquals(200, completed.statusCode(), completed.body());
        assertTrue(completed.body().contains("Java Advanced"), completed.body());
        assertFalse(completed.body().contains("Java Secret"), completed.body());
        assertFalse(request("GET", "/api/courses?q=java", owner, null).body().contains("Java Secret"));
    }

    private String registerAndLogin(String label) throws Exception {
        String email = label + "-" + UUID.randomUUID() + "@example.com";
        assertEquals(201, request("POST", "/api/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\",\"name\":\"Test User\"}").statusCode());
        HttpResponse<String> login = request("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}");
        assertEquals(200, login.statusCode(), login.body());
        return stringField(login.body(), "accessToken");
    }

    private long createWorkspace(String token, String name) throws Exception {
        HttpResponse<String> response = request("POST", "/api/workspaces", token,
                "{\"name\":\"" + name + "\",\"description\":\"Notes\"}");
        assertEquals(201, response.statusCode(), response.body());
        return numberField(response.body(), "id");
    }

    private long createCourse(String token, long workspaceId, String name) throws Exception {
        HttpResponse<String> response = request("POST", "/api/workspaces/" + workspaceId + "/courses", token,
                "{\"name\":\"" + name + "\",\"description\":\"Notes\"}");
        assertEquals(201, response.statusCode(), response.body());
        return numberField(response.body(), "id");
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

    private String stringField(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        assertTrue(matcher.find(), json);
        return matcher.group(1);
    }
}
