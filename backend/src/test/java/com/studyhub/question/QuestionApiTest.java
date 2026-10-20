package com.studyhub.question;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:question_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class QuestionApiTest {
    @Value("${local.server.port}") private int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ownerCanLinkReplaceAndClearQuestionConcept() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        long first = concept(owner, quizId);
        long second = concept(owner, quizId);
        String collection = "/api/quizzes/" + quizId + "/questions";
        HttpResponse<String> created = send("POST", collection, owner, linkedPayload(first));
        assertEquals(201, created.statusCode(), created.body());
        long questionId = json(created).required("id").asLong();
        assertEquals(first, json(created).required("conceptId").asLong());
        String item = "/api/questions/" + questionId;
        assertEquals(first, json(send("GET", item, owner, null)).required("conceptId").asLong());
        assertEquals(first, json(send("GET", collection, owner, null)).get(0).required("conceptId").asLong());

        HttpResponse<String> replaced = send("PUT", item, owner, linkedPayload(second));
        assertEquals(200, replaced.statusCode(), replaced.body());
        assertEquals(second, json(replaced).required("conceptId").asLong());
        assertEquals(second, json(send("GET", item, owner, null)).required("conceptId").asLong());

        HttpResponse<String> cleared = send("PUT", item, owner, linkedPayload(null));
        assertEquals(200, cleared.statusCode(), cleared.body());
        assertTrue(json(cleared).required("conceptId").isNull());
        assertTrue(json(send("GET", item, owner, null)).required("conceptId").isNull());
    }

    @Test
    void rejectsConceptFromDifferentCourseForeignOwnerAndInvalidId() throws Exception {
        String owner = token();
        String other = token();
        long quizId = quiz(owner);
        long sameCourse = concept(owner, quizId);
        long differentCourse = concept(owner, quiz(owner));
        long foreign = concept(other, quiz(other));
        String collection = "/api/quizzes/" + quizId + "/questions";

        assertEquals(400, send("POST", collection, owner, linkedPayload(differentCourse)).statusCode());
        assertEquals(404, send("POST", collection, owner, linkedPayload(foreign)).statusCode());
        assertEquals(404, send("POST", collection, owner, linkedPayload(Long.MAX_VALUE)).statusCode());
        assertEquals(400, send("POST", collection, owner, linkedPayload(0L)).statusCode());
        assertEquals(400, send("POST", collection, owner, linkedPayload(-1L)).statusCode());
        assertEquals(0, json(send("GET", collection, owner, null)).size());

        HttpResponse<String> created = send("POST", collection, owner, linkedPayload(sameCourse));
        assertEquals(201, created.statusCode(), created.body());
        String item = "/api/questions/" + json(created).required("id").asLong();
        assertEquals(400, send("PUT", item, owner, linkedPayload(differentCourse)).statusCode());
        assertEquals(404, send("PUT", item, owner, linkedPayload(foreign)).statusCode());
        JsonNode unchanged = json(send("GET", item, owner, null));
        assertEquals(sameCourse, unchanged.required("conceptId").asLong());
        // Failed updates must also roll back option replacement.
        assertEquals(json(created).required("options"), unchanged.required("options"));
    }

    @Test
    void deletingLinkedConceptPreservesQuestionAndClearsLink() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        long conceptId = concept(owner, quizId);
        HttpResponse<String> created = send("POST", "/api/quizzes/" + quizId + "/questions", owner, linkedPayload(conceptId));
        assertEquals(201, created.statusCode(), created.body());
        String item = "/api/questions/" + json(created).required("id").asLong();
        assertEquals(204, send("DELETE", "/api/concepts/" + conceptId, owner, null).statusCode());
        HttpResponse<String> fetched = send("GET", item, owner, null);
        assertEquals(200, fetched.statusCode(), fetched.body());
        assertTrue(json(fetched).required("conceptId").isNull());
        assertEquals(json(created).required("options"), json(fetched).required("options"));
    }

    private long concept(String token, long quizId) throws Exception {
        HttpResponse<String> quizResponse = send("GET", "/api/quizzes/" + quizId, token, null);
        assertEquals(200, quizResponse.statusCode(), quizResponse.body());
        long courseId = json(quizResponse).required("courseId").asLong();
        HttpResponse<String> created = send("POST", "/api/courses/" + courseId + "/concepts", token,
                "{\"name\":\"Concept-" + UUID.randomUUID() + "\",\"confidence\":50,\"status\":\"LEARNING\"}");
        assertEquals(201, created.statusCode(), created.body());
        return json(created).required("id").asLong();
    }

    private String linkedPayload(Long conceptId) {
        return "{\"text\":\"Linked\",\"type\":\"TRUE_FALSE\",\"conceptId\":" + conceptId
                + ",\"options\":[{\"text\":\"True\",\"correct\":true},{\"text\":\"False\",\"correct\":false}]}";
    }

    @Test
    void ownerCanCreateAndManageQuestionWithOptions() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        String collection = "/api/quizzes/" + quizId + "/questions";
        String first = "{\"text\":\"2 + 2 = ?\",\"type\":\"MULTIPLE_CHOICE\",\"options\":["
                + "{\"text\":\"1\",\"correct\":false},{\"text\":\"2\",\"correct\":false},"
                + "{\"text\":\"3\",\"correct\":false},{\"text\":\"4\",\"correct\":true}]}";
        HttpResponse<String> created = send("POST", collection, owner, first);
        assertEquals(201, created.statusCode(), created.body());
        JsonNode question = json(created);
        long id = question.path("id").asLong();
        assertEquals(quizId, question.path("quizId").asLong());
        assertEquals(4, question.path("options").size());
        assertTrue(question.path("options").get(3).path("correct").asBoolean());
        assertEquals(id, json(send("GET", collection, owner, null)).get(0).path("id").asLong());

        String item = "/api/questions/" + id;
        assertEquals("2 + 2 = ?", json(send("GET", item, owner, null)).path("text").asText());
        String updated = "{\"text\":\"3 + 3 = ?\",\"type\":\"MULTIPLE_CHOICE\",\"options\":["
                + "{\"text\":\"3\",\"correct\":false},{\"text\":\"4\",\"correct\":false},"
                + "{\"text\":\"5\",\"correct\":false},{\"text\":\"6\",\"correct\":true}]}";
        assertEquals(200, send("PUT", item, owner, updated).statusCode());
        JsonNode afterUpdate = json(send("GET", item, owner, null));
        assertEquals("3 + 3 = ?", afterUpdate.path("text").asText());
        assertEquals("6", afterUpdate.path("options").get(3).path("text").asText());
        assertEquals(204, send("DELETE", item, owner, null).statusCode());
        assertEquals(404, send("GET", item, owner, null).statusCode());
    }

    @Test
    void rejectsNullOptionWithoutServerError() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        String body = "{\"text\":\"Question\",\"type\":\"MULTIPLE_CHOICE\",\"options\":[null,{\"text\":\"A\",\"correct\":true}]}";
        assertEquals(400, send("POST", "/api/quizzes/" + quizId + "/questions", owner, body).statusCode());
    }

    @Test
    void rejectsInvalidOptionsForEachQuestionType() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        String collection = "/api/quizzes/" + quizId + "/questions";
        assertEquals(400, send("POST", collection, owner,
                "{\"text\":\"Pick one\",\"type\":\"MULTIPLE_CHOICE\",\"options\":[{\"text\":\"A\",\"correct\":false},{\"text\":\"B\",\"correct\":false}]}").statusCode());
        assertEquals(400, send("POST", collection, owner,
                "{\"text\":\"True?\",\"type\":\"TRUE_FALSE\",\"options\":[{\"text\":\"True\",\"correct\":true}]}").statusCode());
        assertEquals(400, send("POST", collection, owner,
                "{\"text\":\"Answer?\",\"type\":\"SHORT_ANSWER\",\"options\":[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false}]}").statusCode());
        assertEquals(400, send("POST", collection, owner,
                "{\"text\":\"Answer?\",\"type\":\"SHORT_ANSWER\",\"options\":[]}").statusCode());
        assertEquals(400, send("POST", collection, owner,
                "{\"text\":\"Pick\",\"type\":\"MULTIPLE_CHOICE\",\"referenceAnswer\":\"Hidden\",\"options\":[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false},{\"text\":\"C\",\"correct\":false},{\"text\":\"D\",\"correct\":false}]}").statusCode());
        assertEquals(400, send("POST", collection, owner,
                "{\"text\":\"  \",\"type\":\"MULTIPLE_CHOICE\",\"options\":[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false}]}").statusCode());
        assertEquals(0, json(send("GET", collection, owner, null)).size());
    }

    @Test
    void onlyQuizOwnerCanAccessQuestions() throws Exception {
        String owner = token();
        String other = token();
        long quizId = quiz(owner);
        String collection = "/api/quizzes/" + quizId + "/questions";
        String body = "{\"text\":\"True?\",\"type\":\"TRUE_FALSE\",\"options\":[{\"text\":\"True\",\"correct\":true},{\"text\":\"False\",\"correct\":false}]}";
        long id = json(send("POST", collection, owner, body)).path("id").asLong();
        String item = "/api/questions/" + id;
        assertEquals(401, send("GET", collection, null, null).statusCode());
        assertEquals(404, send("GET", collection, other, null).statusCode());
        assertEquals(404, send("POST", collection, other, body).statusCode());
        assertEquals(404, send("GET", item, other, null).statusCode());
        assertEquals(404, send("PUT", item, other, body).statusCode());
        assertEquals(404, send("DELETE", item, other, null).statusCode());
        assertEquals("True?", json(send("GET", item, owner, null)).path("text").asText());
    }

    @Test
    void deletingQuizRemovesItsQuestionsAndOptions() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        String collection = "/api/quizzes/" + quizId + "/questions";
        String body = "{\"text\":\"Answer?\",\"type\":\"SHORT_ANSWER\",\"referenceAnswer\":\"42\",\"options\":[]}";
        long id = json(send("POST", collection, owner, body)).path("id").asLong();
        assertEquals(204, send("DELETE", "/api/quizzes/" + quizId, owner, null).statusCode());
        assertEquals(404, send("GET", "/api/questions/" + id, owner, null).statusCode());
    }

    private String token() throws Exception {
        String email = "question-" + UUID.randomUUID() + "@example.com";
        assertEquals(201, send("POST", "/api/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\",\"name\":\"Test User\"}").statusCode());
        return json(send("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}"))
                .path("accessToken").asText();
    }

    private long quiz(String token) throws Exception {
        long workspaceId = json(send("POST", "/api/workspaces", token,
                "{\"name\":\"Workspace\",\"description\":\"Notes\"}")).path("id").asLong();
        long courseId = json(send("POST", "/api/workspaces/" + workspaceId + "/courses", token,
                "{\"name\":\"Course\",\"description\":\"Notes\"}")).path("id").asLong();
        return json(send("POST", "/api/courses/" + courseId + "/quizzes", token,
                "{\"title\":\"Math\"}")).path("id").asLong();
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return mapper.readTree(response.body());
    }
}
