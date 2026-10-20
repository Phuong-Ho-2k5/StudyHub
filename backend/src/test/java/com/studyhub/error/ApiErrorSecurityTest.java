package com.studyhub.error;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:error_security_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class ApiErrorSecurityTest {
    @Value("${local.server.port}") private int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void missingInvalidAndExpiredTokensReturnTheSameError() throws Exception {
        String path = "/api/workspaces";
        assertUnauthorized(send(path, null));
        assertUnauthorized(send(path, "not-a-jwt"));
        String expired = Jwts.builder().subject("someone@example.com")
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                        "dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc=")), Jwts.SIG.HS256)
                .compact();
        assertUnauthorized(send(path, expired));
        String deletedUserToken = Jwts.builder().subject("deleted-" + UUID.randomUUID() + "@example.com")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                        "dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc=")), Jwts.SIG.HS256)
                .compact();
        assertUnauthorized(send(path, deletedUserToken));
    }

    @Test
    void authenticatedOwnerChecksStillReturnNotFound() throws Exception {
        String email = "security-" + UUID.randomUUID() + "@example.com";
        String register = "{\"name\":\"User\",\"email\":\"" + email + "\",\"password\":\"Secret123!\"}";
        assertEquals(201, post("/api/auth/register", register).statusCode());
        String login = post("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}").body();
        String token = mapper.readTree(login).path("accessToken").asText();
        HttpResponse<String> response = send("/api/workspaces/999999999", token);
        assertEquals(404, response.statusCode(), response.body());
        assertEquals("NOT_FOUND", mapper.readTree(response.body()).path("code").asText());
    }

    private void assertUnauthorized(HttpResponse<String> response) throws Exception {
        assertEquals(401, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        JsonNode body = mapper.readTree(response.body());
        assertEquals("UNAUTHORIZED", body.path("code").asText(), response.body());
        assertEquals("Unauthorized", body.path("message").asText());
        assertEquals("/api/workspaces", body.path("path").asText());
        assertDoesNotThrow(() -> Instant.parse(body.path("timestamp").asText()));
        assertFalse(response.body().contains("not-a-jwt"));
    }

    private HttpResponse<String> send(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
