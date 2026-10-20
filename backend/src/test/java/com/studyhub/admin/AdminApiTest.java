package com.studyhub.admin;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
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
        "spring.datasource.url=jdbc:h2:mem:admin_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class AdminApiTest {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void allAdminEndpointsRequireAdminAndDeniedWritesDoNotMutate() throws Exception {
        Account user = account(false);
        String[] paths = {"/api/admin/users", "/api/admin/users/" + user.id(),
                "/api/admin/users/" + user.id() + "/role", "/api/admin/statistics"};
        for (String token : new String[]{null, "invalid-token", user.token()}) {
            for (String path : paths) {
                int status = token != null && token.equals(user.token()) ? 403 : 401;
                JsonNode error = call(path.endsWith("/role") ? "PATCH" : "GET", path, token,
                        path.endsWith("/role") ? Map.of("role", "ADMIN") : null, status);
                assertEquals(status == 403 ? "FORBIDDEN" : "UNAUTHORIZED", error.path("code").asText());
                assertEquals(path, error.path("path").asText());
            }
        }
        assertEquals("USER", role(user));
    }

    @Test
    void adminListsUsersWithStablePagingAndSafeDetails() throws Exception {
        Account admin = account(true);
        Account target = account(false);
        JsonNode page = call("GET", "/api/admin/users?page=0&size=1", admin.token(), null, 200);
        assertEquals(1, page.path("content").size());
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class).longValue(), page.path("totalElements").asLong());
        long first = jdbc.queryForObject("SELECT MIN(id) FROM users", Long.class);
        assertEquals(first, page.path("content").get(0).path("id").asLong());
        assertTrue(call("GET", "/api/admin/users?page=9999&size=1", admin.token(), null, 200).path("content").isEmpty());
        JsonNode detail = call("GET", "/api/admin/users/" + target.id(), admin.token(), null, 200);
        assertEquals(target.id(), detail.path("id").asLong());
        assertEquals("USER", detail.path("role").asText());
        assertEquals(4, detail.size());
        assertFalse(page.toString().contains("password"));
        assertFalse(detail.toString().contains("password"));
        call("GET", "/api/admin/users/" + Long.MAX_VALUE, admin.token(), null, 404);
    }

    @Test
    void roleChangesPersistAndExistingTokensUseCurrentRole() throws Exception {
        Account admin = account(true);
        Account target = account(false);
        String path = "/api/admin/users/" + target.id() + "/role";
        for (String role : new String[]{"ADMIN", "ADMIN", "USER"}) {
            assertEquals(role, call("PATCH", path, admin.token(), Map.of("role", role), 200).path("role").asText());
            assertEquals(role, role(target));
            call("GET", "/api/admin/statistics", target.token(), null, role.equals("ADMIN") ? 200 : 403);
        }
    }

    @Test
    void invalidRoleAndSelfDemotionLeaveDatabaseUnchanged() throws Exception {
        Account admin = account(true);
        Account target = account(false);
        String path = "/api/admin/users/" + target.id() + "/role";
        for (Object body : new Object[]{Map.of(), Map.of("role", "SUPERUSER"), Map.of("role", 0), "{", "{\"role\":null}"}) {
            call("PATCH", path, admin.token(), body, 400);
        }
        assertEquals("USER", role(target));
        call("PATCH", "/api/admin/users/" + Long.MAX_VALUE + "/role", admin.token(), Map.of("role", "ADMIN"), 404);
        JsonNode error = call("PATCH", "/api/admin/users/" + admin.id() + "/role", admin.token(), Map.of("role", "USER"), 409);
        assertEquals("CONFLICT", error.path("code").asText());
        assertEquals("ADMIN", role(admin));
        call("PATCH", "/api/admin/users/" + admin.id() + "/role", admin.token(), Map.of("role", "ADMIN"), 200);
    }

    @Test
    void statisticsMatchGlobalCountsAcrossOwners() throws Exception {
        Account admin = account(true);
        for (int i = 0; i < 2; i++) {
            Account owner = account(false);
            long workspace = call("POST", "/api/workspaces", owner.token(), Map.of("name", "Workspace", "description", "Test workspace"), 201).path("id").asLong();
            call("POST", "/api/workspaces/" + workspace + "/courses", owner.token(), Map.of("name", "Course", "description", "Test course"), 201);
        }
        JsonNode stats = call("GET", "/api/admin/statistics", admin.token(), null, 200);
        for (String[] field : new String[][]{{"totalUsers", "users"}, {"totalWorkspaces", "workspaces"}, {"totalCourses", "courses"}}) {
            assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM " + field[1], Long.class).longValue(), stats.path(field[0]).asLong());
        }
    }

    private Account account(boolean admin) throws Exception {
        String email = "admin-test-" + UUID.randomUUID() + "@example.com";
        long id = call("POST", "/api/auth/register", null,
                Map.of("name", "Test User", "email", email, "password", "Secret123!"), 201).path("id").asLong();
        assertEquals("USER", jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id));
        if (admin) jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", id);
        String token = call("POST", "/api/auth/login", null,
                Map.of("email", email, "password", "Secret123!"), 200).path("accessToken").asText();
        return new Account(id, token);
    }

    private String role(Account user) {
        return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, user.id());
    }

    private JsonNode call(String method, String path, String token, Object body, int status) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        String payload = body instanceof String text ? text : mapper.writeValueAsString(body);
        builder.header("Content-Type", "application/json").method(method,
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload));
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private record Account(long id, String token) {}
}
