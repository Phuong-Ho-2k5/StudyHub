package com.studyhub.concept;

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
        "spring.datasource.url=jdbc:h2:mem:concept_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class ConceptApiTest {
    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void ownerCanCreateReadUpdateListAndDeleteConcept() throws Exception {
        String owner = registerAndLogin("concept-crud");
        long courseId = createCourse(owner, "Concept course");
        String collection = "/api/courses/" + courseId + "/concepts";
        String initial = "{\"name\":\"Graph\",\"description\":\"Graph theory\",\"confidence\":20,\"status\":\"LEARNING\"}";

        HttpResponse<String> created = request("POST", collection, owner, initial);
        assertEquals(201, created.statusCode(), created.body());
        long conceptId = numberField(created.body(), "id");
        assertEquals(courseId, numberField(created.body(), "courseId"));
        assertTrue(created.body().contains("\"confidence\":20"), created.body());
        assertTrue(created.body().contains("\"status\":\"LEARNING\""), created.body());

        String item = "/api/concepts/" + conceptId;
        assertEquals(200, request("GET", item, owner, null).statusCode());
        HttpResponse<String> listed = request("GET", "/api/concepts?courseId=" + courseId, owner, null);
        assertEquals(200, listed.statusCode(), listed.body());
        assertTrue(listed.body().contains("\"name\":\"Graph\""), listed.body());

        assertEquals(400, request("PUT", item, owner,
                "{\"name\":\"Graph\",\"description\":\"Graph theory\",\"status\":\"LEARNING\"}").statusCode());

        HttpResponse<String> updated = request("PUT", item, owner,
                "{\"name\":\"Advanced Graph\",\"description\":\"Algorithms\",\"confidence\":85,\"status\":\"UNDERSTOOD\"}");
        assertEquals(200, updated.statusCode(), updated.body());
        assertTrue(updated.body().contains("\"name\":\"Advanced Graph\""), updated.body());
        assertTrue(updated.body().contains("\"confidence\":85"), updated.body());
        assertEquals(204, request("DELETE", item, owner, null).statusCode());
        assertEquals(404, request("GET", item, owner, null).statusCode());
        assertEquals("[]", request("GET", "/api/concepts?courseId=" + courseId, owner, null).body());
    }

    @Test
    void conceptAccessRequiresOwningItsCourse() throws Exception {
        String owner = registerAndLogin("concept-owner");
        String other = registerAndLogin("concept-other");
        long courseId = createCourse(owner, "Private course");
        String body = "{\"name\":\"Vectors\",\"description\":\"Notes\",\"confidence\":40,\"status\":\"NEW\"}";
        String collection = "/api/courses/" + courseId + "/concepts";

        assertEquals(401, request("POST", collection, null, body).statusCode());
        assertEquals(404, request("POST", collection, other, body).statusCode());
        HttpResponse<String> created = request("POST", collection, owner, body);
        assertEquals(201, created.statusCode(), created.body());
        String item = "/api/concepts/" + numberField(created.body(), "id");

        assertEquals(404, request("GET", item, other, null).statusCode());
        assertEquals(404, request("PUT", item, other, body).statusCode());
        assertEquals(404, request("DELETE", item, other, null).statusCode());
        assertEquals(404, request("GET", "/api/concepts?courseId=" + courseId, other, null).statusCode());
        assertEquals(200, request("GET", item, owner, null).statusCode());
    }

    @Test
    void rejectsInvalidConceptInput() throws Exception {
        String owner = registerAndLogin("concept-validation");
        long courseId = createCourse(owner, "Validation course");
        String collection = "/api/courses/" + courseId + "/concepts";

        assertEquals(400, request("POST", collection, owner,
                "{\"name\":\"  \",\"confidence\":50,\"status\":\"NEW\"}").statusCode());
        assertEquals(400, request("POST", collection, owner,
                "{\"name\":\"Too low\",\"confidence\":-1,\"status\":\"NEW\"}").statusCode());
        assertEquals(400, request("POST", collection, owner,
                "{\"name\":\"Too high\",\"confidence\":101,\"status\":\"NEW\"}").statusCode());
        assertEquals(400, request("POST", collection, owner,
                "{\"name\":\"No status\",\"confidence\":50}").statusCode());
        assertEquals(400, request("POST", collection, owner,
                "{\"name\":\"No confidence\",\"status\":\"NEW\"}").statusCode());

        HttpResponse<String> listed = request("GET", "/api/concepts?courseId=" + courseId, owner, null);
        assertEquals(200, listed.statusCode(), listed.body());
        assertFalse(listed.body().contains("No confidence"), listed.body());
    }

    @Test
    void filtersConceptsBelowConfidenceWithinOwnedCourse() throws Exception {
        String owner = registerAndLogin("concept-filter-owner");
        String other = registerAndLogin("concept-filter-other");
        long courseId = createCourse(owner, "Filter course");
        long secondCourseId = createCourse(owner, "Other course");
        String collection = "/api/courses/" + courseId + "/concepts";

        assertEquals(201, request("POST", collection, owner,
                "{\"name\":\"Low\",\"confidence\":20,\"status\":\"NEW\"}").statusCode());
        assertEquals(201, request("POST", collection, owner,
                "{\"name\":\"Boundary\",\"confidence\":50,\"status\":\"NEW\"}").statusCode());
        assertEquals(201, request("POST", collection, owner,
                "{\"name\":\"High\",\"confidence\":80,\"status\":\"NEW\"}").statusCode());
        assertEquals(201, request("POST", "/api/courses/" + secondCourseId + "/concepts", owner,
                "{\"name\":\"Other course low\",\"confidence\":10,\"status\":\"NEW\"}").statusCode());

        HttpResponse<String> filtered = request("GET", collection + "?confidenceBelow=50", owner, null);
        assertEquals(200, filtered.statusCode(), filtered.body());
        assertTrue(filtered.body().contains("\"name\":\"Low\""), filtered.body());
        assertFalse(filtered.body().contains("\"name\":\"Boundary\""), filtered.body());
        assertFalse(filtered.body().contains("\"name\":\"High\""), filtered.body());
        assertFalse(filtered.body().contains("Other course low"), filtered.body());
        assertEquals(404, request("GET", collection + "?confidenceBelow=50", other, null).statusCode());
        assertEquals(400, request("GET", collection + "?confidenceBelow=-1", owner, null).statusCode());
        assertEquals(400, request("GET", collection + "?confidenceBelow=101", owner, null).statusCode());
    }

    @Test
    void prerequisiteCanBeListedAndRemovedOnlyByItsOwner() throws Exception {
        String owner = registerAndLogin("prerequisite-owner");
        String other = registerAndLogin("prerequisite-other");
        long courseId = createCourse(owner, "Prerequisite course");
        String collection = "/api/courses/" + courseId + "/concepts";
        String conceptBody = "{\"name\":\"%s\",\"confidence\":20,\"status\":\"NEW\"}";
        long prerequisiteId = numberField(request("POST", collection, owner,
                conceptBody.formatted("Foundation")).body(), "id");
        long conceptId = numberField(request("POST", collection, owner,
                conceptBody.formatted("Advanced")).body(), "id");
        String prerequisites = "/api/concepts/" + conceptId + "/prerequisites";
        String body = "{\"prerequisiteId\":" + prerequisiteId + "}";

        assertEquals(201, request("POST", prerequisites, owner, body).statusCode());
        assertTrue(request("GET", prerequisites, owner, null).body().contains("\"name\":\"Foundation\""));
        assertEquals(400, request("POST", prerequisites, owner, body).statusCode());
        assertEquals(404, request("GET", prerequisites, other, null).statusCode());
        assertEquals(404, request("DELETE", prerequisites + "/" + prerequisiteId, other, null).statusCode());
        assertTrue(request("GET", prerequisites, owner, null).body().contains("\"name\":\"Foundation\""));
        assertEquals(204, request("DELETE", prerequisites + "/" + prerequisiteId, owner, null).statusCode());
        assertEquals("[]", request("GET", prerequisites, owner, null).body());
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

    private long createCourse(String token, String name) throws Exception {
        HttpResponse<String> workspace = request("POST", "/api/workspaces", token,
                "{\"name\":\"Workspace\",\"description\":\"Notes\"}");
        assertEquals(201, workspace.statusCode(), workspace.body());
        long workspaceId = numberField(workspace.body(), "id");
        HttpResponse<String> course = request("POST", "/api/workspaces/" + workspaceId + "/courses", token,
                "{\"name\":\"" + name + "\",\"description\":\"Notes\"}");
        assertEquals(201, course.statusCode(), course.body());
        return numberField(course.body(), "id");
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
