package com.studyhub.config;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:openapi_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class OpenApiApiTest {
    @Value("${local.server.port}") private int port;
    @Autowired @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void documentationAndUiAssetsArePublic() throws Exception {
        assertEquals(200, send("GET", "/v3/api-docs", null, null).statusCode());
        assertEquals(200, send("GET", "/v3/api-docs.yaml", null, null).statusCode());
        assertEquals(200, send("GET", "/v3/api-docs/swagger-config", null, null).statusCode());
        assertEquals(302, send("GET", "/swagger-ui.html", null, null).statusCode());
        var page = send("GET", "/swagger-ui/index.html", null, null);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("swagger-ui"));
        assertEquals(200, send("GET", "/swagger-ui/swagger-ui-bundle.js", null, null).statusCode());
    }

    @Test
    void generatedContractDescribesAuthResponsesPagingAndAllBusinessOperations() throws Exception {
        var response = send("GET", "/v3/api-docs", null, null);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode spec = mapper.readTree(response.body());
        assertTrue(spec.path("openapi").asText().startsWith("3."));
        assertEquals("StudyHub API", spec.at("/info/title").asText());
        assertEquals("http", spec.at("/components/securitySchemes/bearerAuth/type").asText());
        assertEquals("bearer", spec.at("/components/securitySchemes/bearerAuth/scheme").asText());
        assertFalse(spec.path("paths").has("/error"));
        assertFalse(spec.path("paths").has("/actuator/health"));
        spec.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(entry -> {
            JsonNode operation = entry.getValue();
            assertFalse(operation.path("summary").asText().isBlank(), path.getKey());
            assertFalse(operation.path("tags").isEmpty(), path.getKey());
            if (path.getKey().startsWith("/api/auth/")) {
                assertTrue(operation.path("security").isEmpty(), path.getKey());
            } else {
                assertTrue(operation.path("security").path(0).has("bearerAuth"), path.getKey());
                assertTrue(operation.path("responses").has("401"), path.getKey());
            }
            operation.path("parameters").forEach(parameter -> {
                assertNotEquals("currentUser", parameter.path("name").asText());
                assertNotEquals("user", parameter.path("name").asText());
                assertNotEquals("pageable", parameter.path("name").asText());
            });
        }));
        JsonNode paths = spec.path("paths");
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            for (String path : mapping.getPatternValues()) {
                if (path.startsWith("/api/")) {
                    mapping.getMethodsCondition().getMethods().forEach(method ->
                            assertTrue(paths.path(path).has(method.name().toLowerCase(java.util.Locale.ROOT)),
                                    method + " " + path));
                }
            }
        });
        assertTrue(paths.path("/api/workspaces").path("post").path("responses").has("201"));
        assertEquals("#/components/schemas/WorkspaceResponse", paths.path("/api/workspaces")
                .path("post").at("/responses/201/content/*~1*/schema/$ref").asText());
        JsonNode deleted = paths.path("/api/workspaces/{id}").path("delete").path("responses");
        assertTrue(deleted.has("204"));
        assertFalse(deleted.path("204").has("content"));
        assertFalse(deleted.has("200"));
        assertTrue(paths.path("/api/concepts/{id}/prerequisites").path("post")
                .path("responses").has("201"));
        assertTrue(paths.path("/api/admin/users/{id}/role").path("patch").path("responses").has("409"));
        assertTrue(paths.path("/api/study-sessions/{id}/finish").path("post").path("responses").has("409"));
        assertTrue(paths.path("/api/auth/register").path("post").path("responses").has("409"));
        JsonNode paging = paths.path("/api/users/me/quiz-history").path("get").path("parameters");
        assertTrue(paging.findValuesAsText("name").containsAll(java.util.List.of("page", "size", "sort")));
        paths.path("/api/documents").path("get").path("parameters").forEach(parameter ->
                assertFalse(parameter.path("required").asBoolean(), parameter.path("name").asText()));
        assertTrue(spec.at("/components/schemas/ApiErrorResponse/properties").has("fieldErrors"));
        assertEquals("#/components/schemas/ApiErrorResponse", paths.path("/api/workspaces")
                .path("get").at("/responses/401/content/application~1json/schema/$ref").asText());
        JsonNode password = spec.at("/components/schemas/LoginRequest/properties/password");
        assertEquals("password", password.path("format").asText());
        assertFalse(spec.path("components").path("schemas").has("CustomUserDetails"));
    }

    @Test
    void openingDocsDoesNotBypassJwtOrAdminAuthorization() throws Exception {
        assertEquals(401, send("GET", "/api/workspaces", null, null).statusCode());
        assertEquals(401, send("GET", "/api/workspaces", "invalid-token", null).statusCode());
        assertEquals(401, send("GET", "/api/admin/statistics", null, null).statusCode());
        String email = "openapi-" + UUID.randomUUID() + "@example.com";
        assertEquals(201, send("POST", "/api/auth/register", null,
                Map.of("email", email, "password", "Secret123!", "name", "Learner")).statusCode());
        var login = send("POST", "/api/auth/login", null, Map.of("email", email, "password", "Secret123!"));
        assertEquals(200, login.statusCode());
        String token = mapper.readTree(login.body()).path("accessToken").asText();
        assertEquals(200, send("GET", "/api/workspaces", token, null).statusCode());
        assertEquals(200, send("GET", "/api/documents", token, null).statusCode());
        assertEquals(403, send("GET", "/api/admin/statistics", token, null).statusCode());
    }

    @Test void searchContractKeepsDefaultTopKOptionalAndDocumentsLifecycle() throws Exception {
        var spec=mapper.readTree(send("GET","/v3/api-docs",null,null).body());
        var input=spec.at("/components/schemas/CourseSearchRequest");
        assertEquals(5,input.at("/properties/topK/default").asInt());
        assertEquals(1,input.at("/properties/topK/minimum").asInt());
        assertEquals(20,input.at("/properties/topK/maximum").asInt());
        assertFalse(java.util.stream.StreamSupport.stream(input.path("required").spliterator(),false)
                .anyMatch(value->value.asText().equals("topK")));
        var operation=spec.path("paths").path("/api/courses/{courseId}/search").path("post");
        for(String code:java.util.List.of("200","400","401","404","422","502","503","504"))
            assertTrue(operation.path("responses").has(code));
        assertTrue(spec.at("/components/schemas/DocumentResponse/properties").has("publishedIngestionId"));
    }

    private HttpResponse<String> send(String method, String path, String token, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        request.header("Content-Type", "application/json").method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
