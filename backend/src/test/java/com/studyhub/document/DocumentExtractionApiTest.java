package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;

@EnabledIfEnvironmentVariable(named = "AI_SIMULATION_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:document_extract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class DocumentExtractionApiTest {
    static final Path ROOT;
    static { try { ROOT = Files.createTempDirectory("studyhub-extract-api-"); } catch (Exception e) { throw new RuntimeException(e); } }
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        registry.add("studyhub.storage.root", ROOT::toString);
        registry.add("studyhub.ai.url", () -> System.getenv("AI_SIMULATION_URL") + "/success");
        registry.add("studyhub.ai.token", () -> "simulation-token");
    }
    @Value("${local.server.port}") int port;
    @Autowired DocumentService documents;
    @Autowired com.studyhub.user.UserRepository users;
    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();

    @Test void extractsUploadedDocumentThroughAuthenticatedEndpoint() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        call("/api/auth/register", null, Map.of("name", "Extraction", "email", email, "password", "Secret123!"), 201);
        String token = call("/api/auth/login", null, Map.of("email", email, "password", "Secret123!"), 200).path("accessToken").asText();
        long workspaceId = call("/api/workspaces", token, Map.of("name", "Extract", "description", "Test"), 201).path("id").asLong();
        long courseId = call("/api/workspaces/" + workspaceId + "/courses", token,
                Map.of("name", "PDF", "description", "Test"), 201).path("id").asLong();
        byte[] pdf;
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); pdf = output.toByteArray();
        }
        long userId = users.findByEmail(email).orElseThrow().getId();
        var document = documents.upload(courseId, "Bài học", new MockMultipartFile("file", "Bài học.pdf", "application/pdf", pdf), userId);
        var result = call("/api/documents/" + document.id() + "/extract", token, null, 200);
        assertEquals(document.id().longValue(), result.path("documentId").asLong());
        assertEquals("Bài học.pdf", result.path("source").asText());
        assertEquals("StudyHub extracted text", result.path("pages").get(0).path("text").asText());
        assertEquals(DocumentStatus.READY, documents.getDocument(document.id(), userId).status());
    }

    JsonNode call(String path, String token, Object data, int status) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        var response = http.send(request.POST(data == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    @AfterAll static void cleanup() throws Exception {
        try (var paths = Files.walk(ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
