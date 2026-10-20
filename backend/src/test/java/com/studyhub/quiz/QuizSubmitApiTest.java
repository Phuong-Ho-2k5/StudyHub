package com.studyhub.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:quiz_submit_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
class QuizSubmitApiTest {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({"50, true, 60", "50, false, 40", "95, true, 100", "5, false, 0"})
    void gradedAnswerChangesConfidenceWithinBounds(int initial, boolean correct, int expected) throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        long conceptId = concept(owner, quizId, initial);
        JsonNode linked = linkedQuestion(owner, quizId, conceptId, "TRUE_FALSE");
        long unrelated = concept(owner, quizId, 70);
        publish(owner, quizId);

        submitResult(owner, quizId, "{\"answers\":[" + optionAnswer(linked, correct ? 0 : 1) + "]}");

        assertEquals(expected, confidence(owner, conceptId));
        assertEquals(70, confidence(owner, unrelated));
        assertEquals(1, attempts(quizId).size());
    }

    @ParameterizedTest
    @CsvSource({"95, false", "95, true", "5, false", "5, true"})
    void sharedConceptAggregatesBeforeClampingRegardlessOfAnswerOrder(int initial, boolean wrongFirst) throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        long conceptId = concept(owner, quizId, initial);
        JsonNode first = linkedQuestion(owner, quizId, conceptId, "TRUE_FALSE");
        JsonNode second = linkedQuestion(owner, quizId, conceptId, "TRUE_FALSE");
        publish(owner, quizId);
        String right = optionAnswer(first, 0);
        String wrong = optionAnswer(second, 1);

        submitResult(owner, quizId, "{\"answers\":["
                + (wrongFirst ? wrong + "," + right : right + "," + wrong) + "]}");

        assertEquals(initial, confidence(owner, conceptId));
    }

    @Test
    void multipleCorrectAnswersAccumulateForSharedConceptOnEachSubmission() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        long conceptId = concept(owner, quizId, 30);
        JsonNode first = linkedQuestion(owner, quizId, conceptId, "TRUE_FALSE");
        JsonNode second = linkedQuestion(owner, quizId, conceptId, "TRUE_FALSE");
        publish(owner, quizId);
        String body = "{\"answers\":[" + answerItem(first) + "," + answerItem(second) + "]}";

        submitResult(owner, quizId, body);
        assertEquals(50, confidence(owner, conceptId));
        submitResult(owner, quizId, body);
        assertEquals(70, confidence(owner, conceptId));
        assertEquals(2, attempts(quizId).size());
    }

    @ParameterizedTest
    @CsvSource({"50, Reference, 60", "50, Reason, 40", "95, Reference, 100", "5, Reason, 0"})
    void shortAnswerChangesLinkedConfidenceAndUnlinkedAnswersDoNot(int initial, String answerText, int expected) throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        long conceptId = concept(owner, quizId, initial);
        long unrelated = concept(owner, quizId, 70);
        JsonNode shortAnswer = linkedQuestion(owner, quizId, conceptId, "SHORT_ANSWER");
        JsonNode unlinked = question(owner, quizId, "TRUE_FALSE", "Unlinked",
                "[{\"text\":\"True\",\"correct\":true},{\"text\":\"False\",\"correct\":false}]");
        publish(owner, quizId);

        JsonNode result = submitResult(owner, quizId, "{\"answers\":[" + answerItem(unlinked)
                + "," + textAnswer(shortAnswer, answerText) + "]}");

        boolean correct = answerText.equals("Reference");
        assertResult(result, quizId, 2, 2, correct ? 2 : 1, correct ? 0 : 1, 0, correct ? 100.0 : 50.0);
        assertEquals(expected, confidence(owner, conceptId));
        assertEquals(70, confidence(owner, unrelated));
        assertEquals(1, attempts(quizId).size());
    }

    @Test
    void rejectedSubmissionsLeaveConfidenceAndAttemptsUnchanged() throws Exception {
        String owner = token();
        String other = token();
        long quizId = quiz(owner);
        long conceptId = concept(owner, quizId, 50);
        JsonNode linked = linkedQuestion(owner, quizId, conceptId, "TRUE_FALSE");
        JsonNode second = linkedQuestion(owner, quizId, conceptId, "TRUE_FALSE");
        String body = "{\"answers\":[" + answerItem(linked) + "," + answerItem(second) + "]}";
        String path = "/api/quizzes/" + quizId + "/submit";
        assertEquals(400, send("POST", path, owner, body).statusCode());
        publish(owner, quizId);
        assertEquals(404, send("POST", path, other, body).statusCode());
        assertEquals(401, send("POST", path, null, body).statusCode());
        assertEquals(400, send("POST", path, owner, answer(linked)).statusCode());
        // A valid first answer followed by an invalid option must not update confidence early.
        String invalid = "{\"answers\":[" + answerItem(linked) + ",{\"questionId\":"
                + second.path("id").asLong() + ",\"answerOptionIds\":[" + linked.path("options").get(0).path("id").asLong() + "]}]}";
        assertEquals(400, send("POST", path, owner, invalid).statusCode());
        assertEquals(50, confidence(owner, conceptId));
        assertTrue(attempts(quizId).isEmpty());
    }

    private long concept(String token, long quizId, int initial) throws Exception {
        HttpResponse<String> quizResponse = send("GET", "/api/quizzes/" + quizId, token, null);
        assertEquals(200, quizResponse.statusCode(), quizResponse.body());
        long courseId = mapper.readTree(quizResponse.body()).required("courseId").asLong();
        HttpResponse<String> response = send("POST", "/api/courses/" + courseId + "/concepts", token,
                "{\"name\":\"Concept-" + UUID.randomUUID() + "\",\"confidence\":" + initial + ",\"status\":\"LEARNING\"}");
        assertEquals(201, response.statusCode(), response.body());
        return mapper.readTree(response.body()).required("id").asLong();
    }

    private int confidence(String token, long conceptId) throws Exception {
        HttpResponse<String> response = send("GET", "/api/concepts/" + conceptId, token, null);
        assertEquals(200, response.statusCode(), response.body());
        return mapper.readTree(response.body()).required("confidence").asInt();
    }

    private JsonNode linkedQuestion(String token, long quizId, long conceptId, String type) throws Exception {
        String payload = type.equals("SHORT_ANSWER")
                ? "\"referenceAnswer\":\"Reference\",\"options\":[]"
                : "\"options\":[{\"text\":\"True\",\"correct\":true},{\"text\":\"False\",\"correct\":false}]";
        HttpResponse<String> response = send("POST", "/api/quizzes/" + quizId + "/questions", token,
                "{\"text\":\"Linked\",\"type\":\"" + type + "\",\"conceptId\":" + conceptId + "," + payload + "}");
        assertEquals(201, response.statusCode(), response.body());
        JsonNode created = mapper.readTree(response.body());
        assertEquals(conceptId, created.required("conceptId").asLong());
        return created;
    }

    private String optionAnswer(JsonNode question, int optionIndex) {
        return "{\"questionId\":" + question.required("id").asLong() + ",\"answerOptionIds\":["
                + question.required("options").get(optionIndex).required("id").asLong() + "]}";
    }

    @Test
    void successfulSubmitPersistsScoreAndAuthenticatedUser() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        JsonNode choice = question(owner, quizId, "TRUE_FALSE", "True?",
                "[{\"text\":\"True\",\"correct\":true},{\"text\":\"False\",\"correct\":false}]");
        JsonNode shortAnswer = shortQuestion(owner, quizId, "Keyword? Enter one word.", "Reference");
        publish(owner, quizId);

        JsonNode result = submitResult(owner, quizId, "{\"answers\":[" + answerItem(choice)
                + "," + textAnswer(shortAnswer, "Reason") + "]}");

        List<Map<String, Object>> attempts = attempts(quizId);
        assertEquals(1, attempts.size());
        Map<String, Object> saved = attempts.get(0);
        Long ownerId = jdbc.queryForObject("SELECT w.owner_id FROM quizzes q JOIN courses c ON c.id = q.course_id "
                + "JOIN workspaces w ON w.id = c.workspace_id WHERE q.id = ?", Long.class, quizId);
        assertEquals(ownerId.longValue(), ((Number) saved.get("user_id")).longValue());
        assertEquals(quizId, ((Number) saved.get("quiz_id")).longValue());
        assertTrue(((Number) saved.get("id")).longValue() > 0);
        assertTrue(saved.get("created_at") != null);
        assertResult(result, quizId, 2, 2, 1, 1, 0, 50.0);
        assertEquals(2, ((Number) saved.get("total_questions")).intValue());
        assertEquals(2, ((Number) saved.get("graded_count")).intValue());
        assertEquals(1, ((Number) saved.get("correct_count")).intValue());
        assertEquals(1, ((Number) saved.get("incorrect_count")).intValue());
        assertEquals(0, ((Number) saved.get("pending_count")).intValue());
        assertEquals(50.0, ((Number) saved.get("question_percentage")).doubleValue(), 0.000001);
    }

    @Test
    void resubmissionCreatesSeparateAttemptsAndKeepsEarlierScore() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        JsonNode choice = question(owner, quizId, "TRUE_FALSE", "True?",
                "[{\"text\":\"True\",\"correct\":true},{\"text\":\"False\",\"correct\":false}]");
        publish(owner, quizId);
        submitResult(owner, quizId, answer(choice));
        submitResult(owner, quizId, "{\"answers\":[{\"questionId\":" + choice.path("id").asLong()
                + ",\"answerOptionIds\":[" + choice.path("options").get(1).path("id").asLong() + "]}]}");

        assertEquals(200, send("PUT", "/api/questions/" + choice.path("id").asLong(), owner,
                "{\"text\":\"Changed\",\"type\":\"TRUE_FALSE\",\"options\":["
                + "{\"text\":\"True\",\"correct\":false},{\"text\":\"False\",\"correct\":true}]}").statusCode());
        List<Map<String, Object>> saved = attempts(quizId);
        assertEquals(2, saved.size());
        assertFalse(saved.get(0).get("id").equals(saved.get(1).get("id")));
        assertEquals(100.0, ((Number) saved.get(0).get("question_percentage")).doubleValue(), 0.000001);
        assertEquals(0.0, ((Number) saved.get(1).get("question_percentage")).doubleValue(), 0.000001);
        assertEquals(1, ((Number) saved.get(1).get("incorrect_count")).intValue());
    }

    @Test
    void persistedPercentageRetainsResponsePrecision() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        JsonNode choice = question(owner, quizId, "TRUE_FALSE", "True?",
                "[{\"text\":\"True\",\"correct\":true},{\"text\":\"False\",\"correct\":false}]");
        JsonNode shortA = shortQuestion(owner, quizId, "Keyword A?", "Reference");
        JsonNode shortB = shortQuestion(owner, quizId, "Keyword B?", "Reference");
        publish(owner, quizId);
        JsonNode result = submitResult(owner, quizId, "{\"answers\":[" + answerItem(choice)
                + "," + textAnswer(shortA, "A") + "," + textAnswer(shortB, "B") + "]}");
        assertResult(result, quizId, 3, 3, 1, 2, 0, 100.0 / 3);
        assertEquals(33.33333333333333, result.path("questionPercentage").asDouble(), 0.000000001);
        assertEquals(1, attempts(quizId).size());
        assertEquals(33.33333333333333,
                ((Number) attempts(quizId).get(0).get("question_percentage")).doubleValue(), 0.000000001);
    }

    private List<Map<String, Object>> attempts(long quizId) {
        return jdbc.queryForList("SELECT * FROM quiz_attempts WHERE quiz_id = ? ORDER BY id", quizId);
    }

    @Test
    void persistenceFailureDoesNotReportSuccessfulSubmission() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        long conceptId = concept(owner, quizId, 50);
        JsonNode choice = linkedQuestion(owner, quizId, conceptId, "TRUE_FALSE");
        publish(owner, quizId);
        // Reject this fixture's insert at the database boundary without mocking the service.
        jdbc.execute("ALTER TABLE quiz_attempts ADD CONSTRAINT reject_test_attempt CHECK (quiz_id <> " + quizId + ")");
        try {
            HttpResponse<String> failed = send("POST", "/api/quizzes/" + quizId + "/submit", owner, answer(choice));
            assertEquals(500, failed.statusCode(), failed.body());
            assertTrue(attempts(quizId).isEmpty());
            assertEquals(50, confidence(owner, conceptId));
        } finally {
            jdbc.execute("ALTER TABLE quiz_attempts DROP CONSTRAINT reject_test_attempt");
        }
        submitResult(owner, quizId, answer(choice));
        assertEquals(1, attempts(quizId).size());
        assertEquals(60, confidence(owner, conceptId));
    }

    @Test
    void secondConceptUpdateFailureRollsBackConfidenceAndNewAttempt() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        long firstConcept = concept(owner, quizId, 50);
        long secondConcept = concept(owner, quizId, 70);
        JsonNode first = linkedQuestion(owner, quizId, firstConcept, "TRUE_FALSE");
        JsonNode second = linkedQuestion(owner, quizId, secondConcept, "TRUE_FALSE");
        publish(owner, quizId);
        String body = "{\"answers\":[" + answerItem(first) + "," + answerItem(second) + "]}";
        submitResult(owner, quizId, body);
        List<Map<String, Object>> earlierAttempts = attempts(quizId);
        assertEquals(1, earlierAttempts.size());
        assertEquals(60, confidence(owner, firstConcept));
        assertEquals(80, confidence(owner, secondConcept));

        FailSecondConceptUpdateTrigger.updateCount.set(0);
        jdbc.execute("CREATE TRIGGER fail_second_concept_update AFTER UPDATE ON concepts FOR EACH ROW CALL "
                + "'com.studyhub.quiz.FailSecondConceptUpdateTrigger'");
        try {
            HttpResponse<String> failed = send("POST", "/api/quizzes/" + quizId + "/submit", owner, body);
            assertEquals(500, failed.statusCode(), failed.body());
            // The first database update ran before the second one failed, regardless of concept order.
            assertEquals(2, FailSecondConceptUpdateTrigger.updateCount.get());
            assertEquals(60, confidence(owner, firstConcept));
            assertEquals(80, confidence(owner, secondConcept));
            assertEquals(earlierAttempts, attempts(quizId));
        } finally {
            jdbc.execute("DROP TRIGGER fail_second_concept_update");
        }

        submitResult(owner, quizId, body);
        assertEquals(70, confidence(owner, firstConcept));
        assertEquals(90, confidence(owner, secondConcept));
        List<Map<String, Object>> saved = attempts(quizId);
        assertEquals(2, saved.size());
        assertEquals(earlierAttempts.get(0), saved.get(0));
    }

    @Test
    void publishedQuizAcceptsCompleteOptionAndFreeTextAnswers() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        JsonNode choice = question(owner, quizId, "MULTIPLE_CHOICE", "Pick", "[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false},{\"text\":\"C\",\"correct\":false},{\"text\":\"D\",\"correct\":false}]");
        JsonNode shortAnswer = shortQuestion(owner, quizId, "Explain", "Explain the reasoning clearly");
        publish(owner, quizId);
        String body = "{\"answers\":[{\"questionId\":" + choice.path("id").asLong()
                + ",\"answerOptionIds\":[" + choice.path("options").get(1).path("id").asLong() + "]},"
                + "{\"questionId\":" + shortAnswer.path("id").asLong()
                + ",\"answerText\":\" My reasoning. \"}]}";
        JsonNode result = submitResult(owner, quizId, body);
        assertResult(result, quizId, 2, 2, 0, 2, 0, 0.0);
        assertEquals(choice.path("id").asLong(), result.path("questionResults").get(0).path("questionId").asLong());
        assertTrue(result.path("questionResults").get(0).path("isCorrect").isBoolean());
        assertFalse(result.path("questionResults").get(0).path("isCorrect").asBoolean());
        assertEquals(shortAnswer.path("id").asLong(), result.path("questionResults").get(1).path("questionId").asLong());
        assertTrue(result.path("questionResults").get(1).path("isCorrect").isBoolean());
        assertFalse(result.path("questionResults").get(1).path("isCorrect").asBoolean());
    }

    @Test
    void correctChoiceAndIncorrectShortAnswerScoreFiftyPercent() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        JsonNode choice = question(owner, quizId, "MULTIPLE_CHOICE", "Pick",
                "[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false},{\"text\":\"C\",\"correct\":false},{\"text\":\"D\",\"correct\":false}]");
        JsonNode shortAnswer = shortQuestion(owner, quizId, "Explain", "Reference");
        publish(owner, quizId);

        JsonNode result = submitResult(owner, quizId, "{\"answers\":[" + answerItem(choice)
                + ",{\"questionId\":" + shortAnswer.path("id").asLong() + ",\"answerText\":\"Reason\"}]}");

        assertResult(result, quizId, 2, 2, 1, 1, 0, 50.0);
        assertTrue(result.path("questionResults").get(0).path("isCorrect").asBoolean());
        assertTrue(result.path("questionResults").get(1).path("isCorrect").isBoolean());
        assertFalse(result.path("questionResults").get(1).path("isCorrect").asBoolean());
    }

    @Test
    void incorrectShortAnswerScoresZeroPercentImmediately() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        JsonNode shortAnswer = shortQuestion(owner, quizId, "Explain", "Reference");
        publish(owner, quizId);

        JsonNode result = submitResult(owner, quizId, "{\"answers\":[{\"questionId\":"
                + shortAnswer.path("id").asLong() + ",\"answerText\":\"Reason\"}]}");

        assertResult(result, quizId, 1, 1, 0, 1, 0, 0.0);
        assertTrue(result.path("questionResults").get(0).path("isCorrect").isBoolean());
        assertFalse(result.path("questionResults").get(0).path("isCorrect").asBoolean());
    }

    @Test
    void shortAnswerRequiresExactCaseAccentsWhitespaceAndNewlines() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        JsonNode question = shortQuestion(owner, quizId, "Thủ đô? Nhập tên thành phố có dấu.", "Hà Nội");
        publish(owner, quizId);

        JsonNode exact = submitResult(owner, quizId, "{\"answers\":[" + textAnswer(question, "Hà Nội") + "]}");
        assertResult(exact, quizId, 1, 1, 1, 0, 0, 100.0);
        assertTrue(exact.required("questionResults").get(0).required("isCorrect").asBoolean());
        for (String value : new String[] {"hà nội", "Ha Noi", " Hà Nội", "Hà Nội ", "Hà  Nội", "Hà\nNội", "Hà Nội\n"}) {
            JsonNode wrong = submitResult(owner, quizId, "{\"answers\":[" + textAnswer(question, value) + "]}");
            assertResult(wrong, quizId, 1, 1, 0, 1, 0, 0.0);
            assertTrue(wrong.required("questionResults").get(0).required("isCorrect").isBoolean());
            assertFalse(wrong.required("questionResults").get(0).required("isCorrect").asBoolean());
        }
        assertEquals(8, attempts(quizId).size());
    }

    @Test
    void updatedReferencePreservesWhitespaceAndOnlyAffectsFutureSubmissions() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        String reference = " Hà  Nội\nViệt Nam ";
        JsonNode question = shortQuestion(owner, quizId, "Nhập đúng định dạng nhiều dòng.", reference);
        long questionId = question.required("id").asLong();
        assertEquals(reference, jdbc.queryForObject("SELECT reference_answer FROM questions WHERE id = ?", String.class, questionId));
        publish(owner, quizId);
        submitResult(owner, quizId, "{\"answers\":[" + textAnswer(question, reference) + "]}");

        String updatedReference = " Hà Nội\nViệt Nam ";
        String update = mapper.writeValueAsString(Map.of("text", "Nhập đúng định dạng nhiều dòng.",
                "type", "SHORT_ANSWER", "referenceAnswer", updatedReference, "options", List.of()));
        HttpResponse<String> response = send("PUT", "/api/questions/" + questionId, owner, update);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(updatedReference, mapper.readTree(response.body()).required("referenceAnswer").asText());
        HttpResponse<String> fetched = send("GET", "/api/questions/" + questionId, owner, null);
        assertEquals(200, fetched.statusCode(), fetched.body());
        assertEquals(updatedReference, mapper.readTree(fetched.body()).required("referenceAnswer").asText());

        JsonNode wrong = submitResult(owner, quizId, "{\"answers\":[" + textAnswer(question, reference) + "]}");
        assertResult(wrong, quizId, 1, 1, 0, 1, 0, 0.0);
        JsonNode correct = submitResult(owner, quizId, "{\"answers\":[" + textAnswer(question, updatedReference) + "]}");
        assertResult(correct, quizId, 1, 1, 1, 0, 0, 100.0);
        List<Map<String, Object>> saved = attempts(quizId);
        assertEquals(3, saved.size());
        assertEquals(100.0, ((Number) saved.get(0).get("question_percentage")).doubleValue());
        assertEquals(0.0, ((Number) saved.get(1).get("question_percentage")).doubleValue());
        assertEquals(100.0, ((Number) saved.get(2).get("question_percentage")).doubleValue());
    }

    @Test
    void multipleChoiceRequiresExactlyTheCorrectOptionsRegardlessOfOrder() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        JsonNode choice = question(owner, quizId, "MULTIPLE_CHOICE", "Pick two",
                "[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false},{\"text\":\"C\",\"correct\":true},{\"text\":\"D\",\"correct\":false}]");
        publish(owner, quizId);
        long a = choice.path("options").get(0).path("id").asLong();
        long b = choice.path("options").get(1).path("id").asLong();
        long c = choice.path("options").get(2).path("id").asLong();
        String prefix = "{\"answers\":[{\"questionId\":" + choice.path("id").asLong() + ",\"answerOptionIds\":[";

        JsonNode exact = submitResult(owner, quizId, prefix + c + "," + a + "]}]}");
        assertResult(exact, quizId, 1, 1, 1, 0, 0, 100.0);
        assertTrue(exact.path("questionResults").get(0).path("isCorrect").asBoolean());

        for (String selection : new String[] { Long.toString(a), a + "," + b + "," + c, a + "," + b }) {
            JsonNode wrong = submitResult(owner, quizId, prefix + selection + "]}]}");
            assertResult(wrong, quizId, 1, 1, 0, 1, 0, 0.0);
            assertTrue(wrong.path("questionResults").get(0).path("isCorrect").isBoolean());
            assertFalse(wrong.path("questionResults").get(0).path("isCorrect").asBoolean());
        }
    }

    @Test
    void trueFalseWrongAnswerScoresZeroPercent() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        JsonNode question = question(owner, quizId, "TRUE_FALSE", "True?",
                "[{\"text\":\"True\",\"correct\":true},{\"text\":\"False\",\"correct\":false}]");
        publish(owner, quizId);

        JsonNode result = submitResult(owner, quizId, "{\"answers\":[{\"questionId\":"
                + question.path("id").asLong() + ",\"answerOptionIds\":["
                + question.path("options").get(1).path("id").asLong() + "]}]}");

        assertResult(result, quizId, 1, 1, 0, 1, 0, 0.0);
        assertTrue(result.path("questionResults").get(0).path("isCorrect").isBoolean());
        assertFalse(result.path("questionResults").get(0).path("isCorrect").asBoolean());
    }

    @Test
    void rejectsDraftQuizAndForeignUser() throws Exception {
        String owner = token();
        String other = token();
        long quizId = quiz(owner);
        JsonNode question = question(owner, quizId, "TRUE_FALSE", "True?", "[{\"text\":\"True\",\"correct\":true},{\"text\":\"False\",\"correct\":false}]");
        String body = answer(question);
        String path = "/api/quizzes/" + quizId + "/submit";
        assertEquals(400, send("POST", path, owner, body).statusCode());
        publish(owner, quizId);
        assertEquals(404, send("POST", path, other, body).statusCode());
        assertEquals(401, send("POST", path, null, body).statusCode());
        assertEquals(200, send("PUT", "/api/quizzes/" + quizId, owner,
                "{\"title\":\"Quiz\",\"status\":\"ARCHIVED\"}").statusCode());
        assertEquals(400, send("POST", path, owner, body).statusCode());
        assertTrue(attempts(quizId).isEmpty());
    }

    @Test
    void rejectsEmptyQuizAndMalformedRequest() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        publish(owner, quizId);
        String path = "/api/quizzes/" + quizId + "/submit";
        assertEquals(400, send("POST", path, owner, "{\"answers\":[]}").statusCode());
        assertEquals(400, send("POST", path, owner, "{\"answers\":[null]}").statusCode());
        assertEquals(400, send("POST", path, owner, "{\"answers\":[{\"answerText\":\"x\"}]}").statusCode());
        assertTrue(attempts(quizId).isEmpty());
    }

    @Test
    void rejectsMissingDuplicateAndForeignQuestionAnswers() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        JsonNode first = question(owner, quizId, "TRUE_FALSE", "One?", "[{\"text\":\"Yes\",\"correct\":true},{\"text\":\"No\",\"correct\":false}]");
        JsonNode second = question(owner, quizId, "TRUE_FALSE", "Two?", "[{\"text\":\"Yes\",\"correct\":true},{\"text\":\"No\",\"correct\":false}]");
        long otherQuiz = quiz(owner);
        JsonNode foreign = question(owner, otherQuiz, "TRUE_FALSE", "Other?", "[{\"text\":\"Yes\",\"correct\":true},{\"text\":\"No\",\"correct\":false}]");
        publish(owner, quizId);
        String path = "/api/quizzes/" + quizId + "/submit";
        assertEquals(400, send("POST", path, owner, answer(first)).statusCode());
        assertEquals(400, send("POST", path, owner, "{\"answers\":[" + answerItem(first) + "," + answerItem(first) + "]}").statusCode());
        assertEquals(400, send("POST", path, owner, "{\"answers\":[" + answerItem(first) + "," + answerItem(foreign) + "]}").statusCode());
        assertTrue(attempts(quizId).isEmpty());
        JsonNode result = submitResult(owner, quizId,
                "{\"answers\":[" + answerItem(first) + "," + answerItem(second) + "]}");
        assertResult(result, quizId, 2, 2, 2, 0, 0, 100.0);
        assertTrue(result.path("questionResults").get(0).path("isCorrect").asBoolean());
        assertTrue(result.path("questionResults").get(1).path("isCorrect").asBoolean());
    }

    @Test
    void rejectsOptionFromAnotherQuestionAndInvalidAnswerShape() throws Exception {
        String owner = token();
        long quizId = quiz(owner);
        JsonNode first = question(owner, quizId, "TRUE_FALSE", "One?", "[{\"text\":\"Yes\",\"correct\":true},{\"text\":\"No\",\"correct\":false}]");
        JsonNode second = shortQuestion(owner, quizId, "Explain", "Reference");
        JsonNode foreign = question(owner, quiz(owner), "TRUE_FALSE", "Other?", "[{\"text\":\"Yes\",\"correct\":true},{\"text\":\"No\",\"correct\":false}]");
        publish(owner, quizId);
        String path = "/api/quizzes/" + quizId + "/submit";
        long firstId = first.path("id").asLong();
        long secondId = second.path("id").asLong();
        long foreignOptionId = foreign.path("options").get(0).path("id").asLong();
        String textItem = "{\"questionId\":" + secondId + ",\"answerText\":\"Reason\"}";
        assertEquals(400, send("POST", path, owner, "{\"answers\":[{\"questionId\":" + firstId + ",\"answerOptionIds\":[" + foreignOptionId + "]}," + textItem + "]}").statusCode());
        assertEquals(400, send("POST", path, owner, "{\"answers\":[{\"questionId\":" + firstId + ",\"answerOptionIds\":[]}," + textItem + "]}").statusCode());
        long firstOptionId = first.path("options").get(0).path("id").asLong();
        assertEquals(400, send("POST", path, owner, "{\"answers\":[{\"questionId\":" + firstId + ",\"answerOptionIds\":[" + firstOptionId + "," + firstOptionId + "]}," + textItem + "]}").statusCode());
        assertEquals(400, send("POST", path, owner, "{\"answers\":[" + answerItem(first) + ",{\"questionId\":" + secondId + ",\"answerText\":\"  \"}]}").statusCode());
        assertEquals(400, send("POST", path, owner, "{\"answers\":[" + answerItem(first) + ",{\"questionId\":" + secondId + ",\"answerText\":\"" + "a".repeat(4001) + "\"}]}").statusCode());
        assertEquals(400, send("POST", path, owner, "{\"answers\":[" + answerItem(first) + ",{\"questionId\":" + secondId + ",\"answerText\":\"Reason\",\"answerOptionIds\":[" + foreignOptionId + "]}]}").statusCode());
        assertEquals(400, send("POST", path, owner, "{\"answers\":[{\"questionId\":" + firstId + ",\"answerOptionIds\":[" + first.path("options").get(0).path("id").asLong() + "," + first.path("options").get(1).path("id").asLong() + "]}," + textItem + "]}").statusCode());
        assertTrue(attempts(quizId).isEmpty());
    }

    private JsonNode submitResult(String token, long quizId, String body) throws Exception {
        HttpResponse<String> response = send("POST", "/api/quizzes/" + quizId + "/submit", token, body);
        assertEquals(200, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private void assertResult(JsonNode result, long quizId, int total, int graded, int correct,
            int incorrect, int pending, double percentage) {
        assertEquals(quizId, result.required("quizId").asLong());
        assertEquals(total, result.required("totalQuestions").asInt());
        assertEquals(graded, result.required("gradedCount").asInt());
        assertEquals(correct, result.required("correctCount").asInt());
        assertEquals(incorrect, result.required("incorrectCount").asInt());
        assertEquals(pending, result.required("pendingCount").asInt());
        assertTrue(result.path("questionPercentage").isNumber());
        assertEquals(percentage, result.path("questionPercentage").asDouble(), 0.000001);
        assertEquals(total, result.path("questionResults").size());
    }

    private String answer(JsonNode question) {
        return "{\"answers\":[" + answerItem(question) + "]}";
    }

    private String answerItem(JsonNode question) {
        return "{\"questionId\":" + question.path("id").asLong() + ",\"answerOptionIds\":[" + question.path("options").get(0).path("id").asLong() + "]}";
    }

    private JsonNode question(String token, long quizId, String type, String text, String options) throws Exception {
        HttpResponse<String> response = send("POST", "/api/quizzes/" + quizId + "/questions", token,
                "{\"text\":\"" + text + "\",\"type\":\"" + type + "\",\"options\":" + options + "}");
        assertEquals(201, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private JsonNode shortQuestion(String token, long quizId, String text, String referenceAnswer) throws Exception {
        HttpResponse<String> response = send("POST", "/api/quizzes/" + quizId + "/questions", token,
                mapper.writeValueAsString(Map.of("text", text, "type", "SHORT_ANSWER",
                        "referenceAnswer", referenceAnswer, "options", List.of())));
        assertEquals(201, response.statusCode(), response.body());
        JsonNode created = mapper.readTree(response.body());
        assertEquals(referenceAnswer, created.path("referenceAnswer").asText());
        assertTrue(created.path("options").isEmpty());
        return created;
    }

    private String textAnswer(JsonNode question, String answerText) throws Exception {
        return mapper.writeValueAsString(Map.of("questionId", question.required("id").asLong(), "answerText", answerText));
    }

    private void publish(String token, long quizId) throws Exception {
        assertEquals(200, send("PUT", "/api/quizzes/" + quizId, token,
                "{\"title\":\"Quiz\",\"status\":\"PUBLISHED\"}").statusCode());
    }

    private String token() throws Exception {
        String email = "submit-" + UUID.randomUUID() + "@example.com";
        assertEquals(201, send("POST", "/api/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\",\"name\":\"Test User\"}").statusCode());
        return mapper.readTree(send("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"Secret123!\"}").body())
                .path("accessToken").asText();
    }

    private long quiz(String token) throws Exception {
        long workspaceId = mapper.readTree(send("POST", "/api/workspaces", token,
                "{\"name\":\"Workspace\",\"description\":\"Notes\"}").body()).path("id").asLong();
        long courseId = mapper.readTree(send("POST", "/api/workspaces/" + workspaceId + "/courses", token,
                "{\"name\":\"Course\",\"description\":\"Notes\"}").body()).path("id").asLong();
        return mapper.readTree(send("POST", "/api/courses/" + courseId + "/quizzes", token,
                "{\"title\":\"Quiz\"}").body()).path("id").asLong();
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
