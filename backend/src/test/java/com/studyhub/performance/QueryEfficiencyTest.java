package com.studyhub.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.studyhub.concept.*;
import com.studyhub.course.*;
import com.studyhub.question.*;
import com.studyhub.quiz.*;
import com.studyhub.quiz.dto.*;
import com.studyhub.user.*;
import com.studyhub.workspace.Workspace;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:query_efficiency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="
})
@ActiveProfiles("test")
@Transactional
class QueryEfficiencyTest {
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired QuestionService questions;
    @Autowired ConceptService concepts;
    @Autowired QuizSubmissionService submissions;

    @Test
    void listingQuestionsDoesNotSelectOptionsOncePerQuestion() {
        Fixture fixture = fixture();
        Statistics stats = resetStatistics();
        var result = questions.list(fixture.quizId(), fixture.userId());
        assertEquals(6, result.size());
        result.forEach(q -> {
            assertEquals(2, q.options().size());
            assertTrue(q.options().get(0).id() < q.options().get(1).id());
        });
        assertTrue(stats.getPrepareStatementCount() <= 3,
                "Question list executed " + stats.getPrepareStatementCount() + " statements");
    }

    @Test
    void listingPrerequisitesDoesNotSelectEachConceptSeparately() {
        Fixture fixture = fixture();
        Statistics stats = resetStatistics();
        var result = concepts.getPrerequisites(fixture.dependentId(), fixture.userId());
        assertEquals(6, result.size());
        assertTrue(result.stream().allMatch(c -> c.name().startsWith("Prerequisite")));
        assertTrue(stats.getPrepareStatementCount() <= 3,
                "Prerequisite list executed " + stats.getPrepareStatementCount() + " statements");
    }

    @Test
    void submittingQuizLoadsAllConceptsInOneQuery() {
        Fixture fixture = fixture();
        Statistics stats = resetStatistics();
        var result = submissions.submitQuiz(fixture.quizId(),
                new SubmitQuizRequest(fixture.answers()), fixture.userId());
        assertEquals(6, result.correctCount());
        // HQL query count excludes the necessary INSERT/UPDATE statements.
        assertTrue(stats.getQueryExecutionCount() <= 3,
                "Submission executed " + stats.getQueryExecutionCount() + " queries");
        // Detect lazy selects too, independently of the number of writes.
        assertEquals(0, stats.getCollectionFetchCount());
        em.flush();
        em.clear();
        for (Long id : fixture.conceptIds()) {
            assertEquals(60, em.find(Concept.class, id).getConfidence());
        }
    }

    private Statistics resetStatistics() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        return stats;
    }

    @Test
    void submissionRejectsLinkedConceptOwnedByAnotherUser() {
        assertInvalidConceptLink(true, 404);
    }

    @Test
    void submissionRejectsLinkedConceptFromAnotherOwnedCourse() {
        assertInvalidConceptLink(false, 400);
    }

    private void assertInvalidConceptLink(boolean anotherOwner, int expectedStatus) {
        Fixture fixture = fixture();
        User owner = em.find(User.class, fixture.userId());
        if (anotherOwner) {
            owner = new User(UUID.randomUUID() + "@test.local", "hash", "Other", UserRole.USER);
            em.persist(owner);
        }
        Workspace workspace = new Workspace("Other workspace", "", owner);
        em.persist(workspace);
        Course course = new Course("Other course", "", workspace, CourseStatus.IN_PROGRESS);
        em.persist(course);
        Concept foreign = new Concept("Foreign", "", 50, ConceptStatus.LEARNING, course);
        em.persist(foreign);
        // Simulate a legacy/invalid link bypassing QuestionService validation.
        em.find(Question.class, fixture.answers().get(0).questionId()).setConcept(foreign);
        Long foreignId = foreign.getId();
        em.flush();
        em.clear();
        var error = assertThrows(ResponseStatusException.class, () -> submissions.submitQuiz(
                fixture.quizId(), new SubmitQuizRequest(fixture.answers()), fixture.userId()));
        assertEquals(expectedStatus, error.getStatusCode().value());
        assertEquals(0L, em.createQuery("select count(a) from QuizAttempt a where a.quiz.id = :quizId", Long.class)
                .setParameter("quizId", fixture.quizId()).getSingleResult());
        for (Long id : fixture.conceptIds()) {
            assertEquals(50, em.find(Concept.class, id).getConfidence());
        }
        assertEquals(50, em.find(Concept.class, foreignId).getConfidence());
    }

    private Fixture fixture() {
        User user = new User(UUID.randomUUID() + "@test.local", "hash", "Owner", UserRole.USER);
        em.persist(user);
        Workspace workspace = new Workspace("Workspace", "", user);
        em.persist(workspace);
        Course course = new Course("Course", "", workspace, CourseStatus.IN_PROGRESS);
        em.persist(course);
        Quiz quiz = new Quiz("Quiz", course);
        quiz.setStatus(QuizStatus.PUBLISHED);
        em.persist(quiz);
        Concept dependent = new Concept("Dependent", "", 50, ConceptStatus.LEARNING, course);
        em.persist(dependent);
        List<SubmitAnswerRequest> answers = new ArrayList<>();
        List<Long> conceptIds = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Concept concept = new Concept("Prerequisite " + i, "", 50, ConceptStatus.LEARNING, course);
            em.persist(concept);
            conceptIds.add(concept.getId());
            em.persist(new ConceptDependency(concept, dependent));
            Question question = new Question(quiz, "Question " + i, QuestionType.TRUE_FALSE, null);
            question.setConcept(concept);
            question.addOption("True", true);
            question.addOption("False", false);
            em.persist(question);
            em.flush();
            answers.add(new SubmitAnswerRequest(question.getId(),
                    List.of(question.getOptions().get(0).getId()), null));
        }
        Fixture fixture = new Fixture(user.getId(), quiz.getId(), dependent.getId(), answers, conceptIds);
        em.flush();
        em.clear();
        return fixture;
    }

    private record Fixture(Long userId, Long quizId, Long dependentId,
                           List<SubmitAnswerRequest> answers, List<Long> conceptIds) {}
}
