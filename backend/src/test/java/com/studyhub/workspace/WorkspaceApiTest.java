package com.studyhub.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class WorkspaceApiTest {
    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void workspaceCrudIsRestrictedToJwtOwner() throws Exception {
        request("POST", "/api/auth/register", null,
                "{\"email\":\"owner@example.com\",\"password\":\"Secret123!\",\"name\":\"Owner\"}");
        request("POST", "/api/auth/register", null,
                "{\"email\":\"other@example.com\",\"password\":\"Secret123!\",\"name\":\"Other\"}");
        String ownerToken = tokenFor("owner@example.com");
        String otherToken = tokenFor("other@example.com");

        assertEquals(401, request("GET", "/api/workspaces", null, null).statusCode());
        HttpResponse<String> created = request("POST", "/api/workspaces", ownerToken,
                "{\"name\":\"Math\",\"description\":\"Notes\"}");
        assertEquals(201, created.statusCode());
        long workspaceId = numberField(created.body(), "id");
        String originalUpdatedAt = stringField(created.body(), "updatedAt");

        assertTrue(request("GET", "/api/workspaces", ownerToken, null).body().contains("Math"));
        assertEquals("[]", request("GET", "/api/workspaces", otherToken, null).body());
        assertEquals(404, request("GET", "/api/workspaces/" + workspaceId, otherToken, null).statusCode());
        assertEquals(404, request("PUT", "/api/workspaces/" + workspaceId, otherToken,
                "{\"name\":\"Stolen\",\"description\":\"Notes\"}").statusCode());
        assertEquals(404, request("DELETE", "/api/workspaces/" + workspaceId, otherToken, null).statusCode());

        Thread.sleep(30);
        HttpResponse<String> updated = request("PUT", "/api/workspaces/" + workspaceId, ownerToken,
                "{\"name\":\"Algebra\",\"description\":\"Notes\"}");
        assertEquals(200, updated.statusCode());
        HttpResponse<String> fetched = request("GET", "/api/workspaces/" + workspaceId, ownerToken, null);
        assertTrue(fetched.body().contains("Algebra"));
        assertNotEquals(originalUpdatedAt, stringField(updated.body(), "updatedAt"));
        assertEquals(stringField(fetched.body(), "updatedAt"), stringField(updated.body(), "updatedAt"));
        assertEquals(204, request("DELETE", "/api/workspaces/" + workspaceId, ownerToken, null).statusCode());
        assertEquals(404, request("GET", "/api/workspaces/" + workspaceId, ownerToken, null).statusCode());
    }

    private String tokenFor(String email) throws Exception {
        HttpResponse<String> login = request("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}");
        assertEquals(200, login.statusCode());
        return stringField(login.body(), "accessToken");
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
