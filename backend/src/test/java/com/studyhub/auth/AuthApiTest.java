package com.studyhub.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_api_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class AuthApiTest {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwords;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void registeredAccountHasHashedPasswordAndCanUseJwtOnProtectedEndpoint() throws Exception {
        String email = email();
        var registered = post("register", Map.of("email", email.toUpperCase(Locale.ROOT),
                "password", "Secret123!", "name", "Learner", "role", "ADMIN"));
        assertEquals(201, registered.statusCode(), registered.body());
        JsonNode body = mapper.readTree(registered.body());
        long id = body.path("id").asLong();
        assertTrue(id > 0);
        assertEquals(email, body.path("email").asText());
        assertEquals("Learner", body.path("name").asText());
        assertEquals(3, body.size());
        assertEquals("USER", jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id));
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id);
        assertNotEquals("Secret123!", hash);
        assertTrue(passwords.matches("Secret123!", hash));

        var login = post("login", Map.of("email", email.toUpperCase(Locale.ROOT), "password", "Secret123!"));
        assertEquals(200, login.statusCode(), login.body());
        JsonNode result = mapper.readTree(login.body());
        assertEquals(1, result.size());
        String token = result.path("accessToken").asText();
        assertFalse(token.isBlank());
        assertEquals(200, get("/api/workspaces", token).statusCode());
        assertEquals(403, get("/api/admin/users", token).statusCode());
    }

    @Test
    void duplicateEmailWithDifferentCasePreservesOriginalAccount() throws Exception {
        String email = email();
        assertEquals(201, post("register", Map.of("email", email, "password", "Secret123!", "name", "Original")).statusCode());

        var duplicate = post("register", Map.of("email", email.toUpperCase(Locale.ROOT),
                "password", "Different123!", "name", "Replacement"));

        assertError(duplicate, 409, "CONFLICT", "/api/auth/register");
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Long.class, email));
        assertEquals("Original", jdbc.queryForObject("SELECT name FROM users WHERE email = ?", String.class, email));
        assertEquals(200, post("login", Map.of("email", email, "password", "Secret123!")).statusCode());
        assertEquals(401, post("login", Map.of("email", email, "password", "Different123!")).statusCode());
    }

    @Test
    void unknownEmailAndWrongPasswordReturnSamePublicErrorWithoutToken() throws Exception {
        String email = email();
        assertEquals(201, post("register", Map.of("email", email, "password", "Secret123!", "name", "Learner")).statusCode());
        var wrongPassword = post("login", Map.of("email", email, "password", "WrongPass123!"));
        var unknownEmail = post("login", Map.of("email", email(), "password", "Secret123!"));

        for (var response : new HttpResponse<?>[]{wrongPassword, unknownEmail}) {
            JsonNode error = assertError(response, 401, "UNAUTHORIZED", "/api/auth/login");
            assertEquals("Invalid email or password", error.path("message").asText());
            assertFalse(error.has("accessToken"));
        }
    }

    @ParameterizedTest
    @CsvSource({"email,not-an-email", "password,short", "name,' '"})
    void invalidRegistrationDoesNotCreateAccount(String field, String invalidValue) throws Exception {
        String email = email();
        var payload = new java.util.HashMap<String, String>(Map.of(
                "email", email, "password", "Secret123!", "name", "Learner"));
        payload.put(field, invalidValue);
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);

        JsonNode error = assertError(post("register", payload), 400, "VALIDATION_ERROR", "/api/auth/register");

        assertTrue(error.path("fieldErrors").has(field));
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class).longValue());
    }

    @ParameterizedTest
    @CsvSource({"email,not-an-email", "password,short"})
    void invalidLoginPayloadReturnsValidationError(String field, String invalidValue) throws Exception {
        var payload = new java.util.HashMap<String, String>(Map.of("email", email(), "password", "Secret123!"));
        payload.put(field, invalidValue);

        JsonNode error = assertError(post("login", payload), 400, "VALIDATION_ERROR", "/api/auth/login");

        assertTrue(error.path("fieldErrors").has(field));
        assertFalse(error.has("accessToken"));
    }

    private JsonNode assertError(HttpResponse<?> response, int status, String code, String path) throws Exception {
        assertEquals(status, response.statusCode(), response.body().toString());
        JsonNode error = mapper.readTree(response.body().toString());
        assertEquals(code, error.path("code").asText());
        assertEquals(path, error.path("path").asText());
        assertFalse(error.has("password"));
        assertFalse(error.has("passwordHash"));
        return error;
    }

    private String email() {
        return "auth-" + UUID.randomUUID() + "@example.com";
    }

    private HttpResponse<String> post(String action, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/" + action))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
