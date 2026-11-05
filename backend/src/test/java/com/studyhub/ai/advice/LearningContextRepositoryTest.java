package com.studyhub.ai.advice;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:advice_context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "studyhub.ai.token=", "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
class LearningContextRepositoryTest extends AdviceApiSupport {
    @Autowired LearningContextService contexts;

    @Test void scopesAggregatesAndUsesCurrentConfidenceThresholds() throws Exception {
        var owner = owner(); var other = owner();
        concept(owner, "A", 49); concept(owner, "B", 50); concept(owner, "C", 79); concept(owner, "D", 80);
        concept(other, "Foreign", 0);
        long allowed = attempt(owner, owner.userId(), 20);
        attempt(owner, other.userId(), 90); attempt(other, owner.userId(), 95);
        jdbc.update("insert into study_sessions(user_id,course_id,start_time,end_time,duration_seconds) values (?,?,CURRENT_TIMESTAMP - INTERVAL '120' SECOND,CURRENT_TIMESTAMP,120)", owner.userId(), owner.courseId());
        jdbc.update("insert into study_sessions(user_id,course_id,start_time) values (?,?,CURRENT_TIMESTAMP)", owner.userId(), owner.courseId());
        jdbc.update("insert into study_sessions(user_id,course_id,start_time,end_time,duration_seconds) values (?,?,CURRENT_TIMESTAMP - INTERVAL '900' SECOND,CURRENT_TIMESTAMP,900)", other.userId(), owner.courseId());
        var snapshot = contexts.build(owner.courseId(), owner.userId());
        var progress = fact(snapshot, "progress");
        assertEquals(4, progress.path("totalConcepts").asInt());
        assertEquals(64.5, progress.path("progressPercentage").asDouble());
        assertEquals(25.0, progress.path("masteryPercentage").asDouble());
        assertEquals(1, progress.path("needsReviewConcepts").asInt());
        assertEquals(2, progress.path("developingConcepts").asInt());
        assertEquals(1, progress.path("masteredConcepts").asInt());
        assertEquals(1, snapshot.facts().stream().filter(f -> f.type().equals("QUIZ_ATTEMPT")).count());
        assertEquals(20, fact(snapshot, "attempt:" + allowed).path("questionPercentage").asDouble());
        assertTrue(java.time.Instant.parse(fact(snapshot,"attempt:"+allowed).path("createdAt").asText())
            .isBefore(java.time.Instant.now().plusSeconds(5)), "Attempt timestamp must not be shifted into the future by the JVM timezone");
        assertEquals(120, fact(snapshot, "study-summary").path("totalStudySeconds").asLong());
        assertEquals(1, fact(snapshot, "study-summary").path("completedStudySessions").asInt());
        assertTrue(snapshot.hasLearningData());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> contexts.build(owner.courseId(), other.userId())).getStatusCode().value());
    }

    @Test void boundsSelectionWithoutTruncatingOverallProgressAndOrdersTies() throws Exception {
        var owner = owner(); var ids = new ArrayList<Long>();
        for (int i=0;i<11;i++) ids.add(concept(owner, "Concept " + i, 10));
        for (int i=0;i<3;i++) for (int j=3;j<11;j++)
            jdbc.update("insert into concept_dependencies(concept_id,prerequisite_id) values (?,?)", ids.get(i), ids.get(j));
        for (int i=0;i<6;i++) attempt(owner, owner.userId(), i);
        var snapshot = contexts.build(owner.courseId(), owner.userId());
        assertEquals(11, fact(snapshot,"progress").path("totalConcepts").asInt());
        assertEquals(ids.subList(0,10).stream().map(id -> "concept:"+id).toList(), snapshot.facts().stream().filter(f -> f.type().equals("CONCEPT")).map(f -> f.id()).toList());
        assertEquals(20, snapshot.facts().stream().filter(f -> f.type().equals("PREREQUISITE")).count());
        assertEquals(5, snapshot.facts().stream().filter(f -> f.type().equals("QUIZ_ATTEMPT")).count());
        assertTrue(snapshot.limitations().containsAll(List.of("CONCEPT_SELECTION_LIMIT", "PREREQUISITE_SELECTION_LIMIT")));
        assertFalse(snapshot.retrievalHint().contains("Concept 3"));
    }

    @Test void emptyCourseIsColdStartAndDoesNotTreatZeroSummaryAsActivity() throws Exception {
        var owner = owner(); var snapshot = contexts.build(owner.courseId(), owner.userId());
        assertFalse(snapshot.hasLearningData());
        assertTrue(snapshot.limitations().containsAll(List.of("NO_CONCEPTS", "NO_QUIZ_ATTEMPTS", "NO_COMPLETED_STUDY_SESSIONS")));
        assertEquals(0, fact(snapshot,"progress").path("progressPercentage").asDouble());
        assertTrue(fact(snapshot,"study-summary").path("lastCompletedAt").isNull());
    }

    private com.fasterxml.jackson.databind.JsonNode fact(LearningAdviceModels.LearningSnapshot snapshot, String id) {
        return json.valueToTree(snapshot.facts().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow().data());
    }
}
