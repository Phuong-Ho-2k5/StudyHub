package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:document_files;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc=",
    "spring.servlet.multipart.max-file-size=4KB", "spring.servlet.multipart.max-request-size=8KB"
})
@ActiveProfiles("test")
class DocumentFileApiTest {
    static final Path ROOT;
    static { try { ROOT = Files.createTempDirectory("studyhub-files-test-"); } catch (Exception e) { throw new RuntimeException(e); } }
    @DynamicPropertySource static void storage(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("studyhub.storage.root", ROOT::toString);
        // Explicitly exclude developer .env credentials from tests.
        registry.add("studyhub.ai.token", () -> "");
    }
    @Value("${local.server.port}") int port;
    final HttpClient client = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();
    String token;
    long workspaceId;
    long courseId;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired DocumentService documents;
    @Autowired com.studyhub.document.storage.FileDeletionWorker deletionWorker;

    @BeforeEach void setup() throws Exception {
        token = login();
        var workspace = request("POST", "/api/workspaces", token, Map.of("name", "Files", "description", "Notes"));
        assertEquals(201, workspace.statusCode(), workspace.body());
        workspaceId = tree(workspace).path("id").asLong();
        var course = request("POST", "/api/workspaces/" + workspaceId + "/courses", token, Map.of("name", "PDF", "description", "Notes"));
        assertEquals(201, course.statusCode(), course.body());
        courseId = tree(course).path("id").asLong();
    }

    @AfterAll static void removeTestFiles() throws Exception {
        try (var paths = Files.walk(ROOT)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }

    @Test void uploadsExactBytesAndDoesNotExposeDownloadEndpoint() throws Exception {
        byte[] pdf = pdf();
        var response = upload(token, "lecture.pdf", "application/pdf", pdf, "Bài học");
        assertEquals(201, response.statusCode(), response.body());
        var doc = tree(response);
        assertTrue(doc.path("managedFile").asBoolean());
        assertEquals("Bài học", doc.path("title").asText());
        String key = doc.path("storagePath").asText();
        assertTrue(key.matches("[a-f0-9-]{36}\\.pdf"), key);
        assertArrayEquals(pdf, Files.readAllBytes(ROOT.resolve(key)));
        String path = "/api/documents/" + doc.path("id").asLong() + "/download";
        var download = client.send(builder(path, token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(404, download.statusCode());
        assertEquals(404, request("GET", path, login(), null).statusCode());
        assertEquals(401, request("GET", path, null, null).statusCode());
    }

    @Test void extractionChecksOwnershipAndNeverReadsLegacyPaths() throws Exception {
        var doc = tree(upload(token, "test.pdf", "application/pdf", pdf(), "PDF"));
        String path = "/api/documents/" + doc.path("id").asLong() + "/extract";
        assertEquals(401, request("POST", path, null, null).statusCode());
        assertEquals(404, request("POST", path, login(), null).statusCode());
        var legacy = tree(request("POST", "/api/courses/" + courseId + "/documents", token,
                Map.of("title", "Legacy", "fileName", "secret.pdf", "fileType", "application/pdf", "storagePath", "C:/private/secret.pdf")));
        assertEquals(409, request("POST", "/api/documents/" + legacy.path("id").asLong() + "/extract", token, null).statusCode());
    }

    @Test void extractionFailsClosedWithoutAiCredentials() throws Exception {
        var doc = tree(upload(token, "test.pdf", "application/pdf", pdf(), "PDF"));
        var response = request("POST", "/api/documents/" + doc.path("id").asLong() + "/extract", token, null);
        assertEquals(503, response.statusCode(), response.body());
    }

    @Test void extractionRejectsMissingManagedFile() throws Exception {
        var doc = tree(upload(token, "test.pdf", "application/pdf", pdf(), "PDF"));
        Files.delete(ROOT.resolve(doc.path("storagePath").asText()));
        var response = request("POST", "/api/documents/" + doc.path("id").asLong() + "/extract", token, null);
        assertEquals(409, response.statusCode(), response.body());
    }

    @Test void rejectsInvalidPdfEmptyFileAndInvalidTitleWithoutLeavingFiles() throws Exception {
        long before = countFiles();
        assertEquals(415, upload(token, "fake.pdf", "application/pdf", "%PDF-fake".getBytes(), "PDF").statusCode());
        assertEquals(415, upload(token, "notes.txt", "text/plain", pdf(), "PDF").statusCode());
        assertEquals(400, upload(token, "empty.pdf", "application/pdf", new byte[0], "PDF").statusCode());
        assertEquals(400, upload(token, "lecture.pdf", "application/pdf", pdf(), " ").statusCode());
        assertEquals(400, upload(token, "lecture.pdf", "application/pdf", pdf(), "x".repeat(201)).statusCode());
        assertEquals(before, countFiles());
    }

    @Test void rejectsOversizedFileWithJsonError() throws Exception {
        var response = upload(token, "large.pdf", "application/pdf", new byte[5000], "PDF");
        assertEquals(413, response.statusCode(), response.body());
        assertTrue(tree(response).has("code"));
    }

    @Test void rejectsForeignCourseBeforeWritingAndSanitizesOriginalFilename() throws Exception {
        long before = countFiles();
        assertEquals(404, upload(login(), "test.pdf", "application/pdf", pdf(), "PDF").statusCode());
        assertEquals(before, countFiles());
        var response = upload(token, "../../bai-hoc.pdf", "application/pdf", pdf(), "PDF");
        assertEquals(201, response.statusCode(), response.body());
        assertEquals("bai-hoc.pdf", tree(response).path("fileName").asText());
    }

    @Test void deletesFilesForDocumentCourseAndWorkspace() throws Exception {
        for (String scope : List.of("document", "course", "workspace")) {
            var response = upload(token, "test.pdf", "application/pdf", pdf(), "PDF");
            assertEquals(201, response.statusCode(), response.body());
            var doc = tree(response);
            Path file = ROOT.resolve(doc.path("storagePath").asText());
            String path = switch (scope) {
                case "document" -> "/api/documents/" + doc.path("id").asLong();
                case "course" -> "/api/courses/" + courseId;
                default -> "/api/workspaces/" + workspaceId;
            };
            assertEquals(204, request("DELETE", path, token, null).statusCode());
            assertFalse(Files.exists(file), scope);
            if (scope.equals("course")) courseId = tree(request("POST", "/api/workspaces/" + workspaceId + "/courses", token, Map.of("name", "Next", "description", "Notes"))).path("id").asLong();
        }
    }

    @Test void legacyMetadataCannotDeleteArbitraryFile() throws Exception {
        Path file = ROOT.resolve("important.pdf");
        Files.write(file, pdf());
        var legacy = tree(request("POST", "/api/courses/" + courseId + "/documents", token,
                Map.of("title", "Legacy", "fileName", "important.pdf", "fileType", "application/pdf", "storagePath", file.toString())));
        String path = "/api/documents/" + legacy.path("id").asLong();
        assertEquals(404, request("GET", path + "/download", token, null).statusCode());
        assertEquals(204, request("DELETE", path, token, null).statusCode());
        assertTrue(Files.exists(file));
    }

    @Test void managedFileMetadataCannotBeRedirected() throws Exception {
        var doc = tree(upload(token, "test.pdf", "application/pdf", pdf(), "PDF"));
        String path = "/api/documents/" + doc.path("id").asLong();
        var data = new HashMap<String, Object>();
        data.put("title", "Renamed"); data.put("fileName", "test.pdf"); data.put("fileType", "application/pdf");
        data.put("storagePath", doc.path("storagePath").asText());
        assertEquals(200, request("PUT", path, token, data).statusCode());
        data.put("storagePath", "other.pdf");
        assertEquals(400, request("PUT", path, token, data).statusCode());
    }

    @Test void rollbackRemovesNewUploadAndRetainsDeletedDocumentFile() throws Exception {
        long userId = jdbc.queryForObject("SELECT w.owner_id FROM courses c JOIN workspaces w ON w.id=c.workspace_id WHERE c.id=?", Long.class, courseId);
        long before = countFiles();
        byte[] pdf = pdf();
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            documents.upload(courseId, "Rollback", new org.springframework.mock.web.MockMultipartFile("file", "test.pdf", "application/pdf", pdf), userId);
            status.setRollbackOnly();
        });
        assertEquals(before, countFiles());
        var doc = tree(upload(token, "test.pdf", "application/pdf", pdf, "PDF"));
        Path file = ROOT.resolve(doc.path("storagePath").asText());
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            documents.deleteDocument(doc.path("id").asLong(), userId);
            status.setRollbackOnly();
        });
        assertTrue(Files.exists(file));
        assertEquals(200, request("GET", "/api/documents/" + doc.path("id").asLong(), token, null).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM file_deletion_tasks", Integer.class));
    }

    @Test void failedDeletionRemainsQueuedAndCanRetry() throws Exception {
        var doc = tree(upload(token, "test.pdf", "application/pdf", pdf(), "PDF"));
        String key = doc.path("storagePath").asText();
        Path file = ROOT.resolve(key);
        // Make deletion fail on every OS without relying on platform-specific file permissions.
        Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("blocker"), "busy");
        assertEquals(204, request("DELETE", "/api/documents/" + doc.path("id").asLong(), token, null).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM file_deletion_tasks WHERE storage_key=?", Integer.class, key));
        Files.delete(file.resolve("blocker"));
        deletionWorker.processPending();
        assertFalse(Files.exists(file));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM file_deletion_tasks WHERE storage_key=?", Integer.class, key));
    }

    byte[] pdf() throws Exception {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage()); doc.save(out); return out.toByteArray();
        }
    }
    long countFiles() throws Exception { try (var files = Files.list(ROOT)) { return files.count(); } }
    String login() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        assertEquals(201, request("POST", "/api/auth/register", null, Map.of("name", "Test", "email", email, "password", "Secret123!")).statusCode());
        return tree(request("POST", "/api/auth/login", null, Map.of("email", email, "password", "Secret123!"))).path("accessToken").asText();
    }
    HttpRequest.Builder builder(String path, String auth) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (auth != null) builder.header("Authorization", "Bearer " + auth);
        return builder;
    }
    HttpResponse<String> request(String method, String path, String auth, Object data) throws Exception {
        return client.send(builder(path, auth).header("Content-Type", "application/json").method(method,
                data == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(), HttpResponse.BodyHandlers.ofString());
    }
    JsonNode tree(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    HttpResponse<String> upload(String auth, String name, String type, byte[] bytes, String title) throws Exception {
        String boundary = "StudyHubBoundary";
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\n" + title
                + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + name
                + "\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bytes); body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return client.send(builder("/api/courses/" + courseId + "/documents/upload", auth)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }
}
