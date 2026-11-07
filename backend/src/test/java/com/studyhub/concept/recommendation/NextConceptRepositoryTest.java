package com.studyhub.concept.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.studyhub.ai.advice.LearningContextRepository;
import com.studyhub.security.ResourceAccessService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:recommendations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "studyhub.ai.token=", "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
class NextConceptRepositoryTest extends RecommendationTestSupport {
    @Autowired NextConceptRepository repository;

    @Test void loadsWholeCourseBeyondAdviceSelectionAndKeepsQueryCountConstant() throws Exception {
        var owner = owner();
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 12; i++) ids.add(concept(owner, "Node " + i, 20));
        for (int i = 0; i < 3; i++) for (int j = 3; j < 12; j++) edge(ids.get(i), ids.get(j));
        var counted = new CountingDataSource(Objects.requireNonNull(jdbc.getDataSource()));
        var graph = new NextConceptRepository(new JdbcTemplate(counted)).loadGraph(owner.courseId(), owner.userId());
        assertEquals(12, graph.concepts().size());
        assertEquals(27, graph.edges().size());
        assertEquals(2, counted.statements());
        counted.reset();
        var started=System.nanoTime();
        new NextConceptRepository(new JdbcTemplate(counted)).loadGraph(owner.courseId(), owner.userId());
        assertEquals(2,counted.statements());
        System.out.println("Graph fixture: 12 nodes/27 edges, JDBC statements="+counted.statements()+", elapsedMs="+(System.nanoTime()-started)/1_000_000);
    }

    @Test void isolatesOwnerAndCourseAndFlagsForeignPrerequisite() throws Exception {
        var owner = owner(); var foreign = owner();
        long local = concept(owner, "Local", 10), other = concept(foreign, "Secret", 5);
        long workspace = jdbc.queryForObject("select workspace_id from courses where id=?", Long.class, owner.courseId());
        jdbc.update("insert into courses(workspace_id,name) values (?,?)", workspace, "Sibling");
        long sibling = jdbc.queryForObject("select id from courses where workspace_id=? and name='Sibling'", Long.class, workspace);
        jdbc.update("insert into concepts(course_id,name,confidence,status) values (?,'Sibling concept',1,'NEW')", sibling);
        edge(local, other);
        var graph = repository.loadGraph(owner.courseId(), owner.userId());
        assertEquals(List.of(local), graph.concepts().stream().map(NextConceptModels.ConceptNode::id).toList());
        assertNull(graph.edges().getFirst().prerequisiteId());
        assertTrue(repository.loadGraph(owner.courseId(), foreign.userId()).concepts().isEmpty());
        assertTrue(repository.loadGraph(owner.courseId(), foreign.userId()).edges().isEmpty());
    }

    @Test void acceptsConceptCapAndRejectsCapPlusOne() throws Exception {
        var owner = owner();
        batchConcepts(owner, 2000);
        long started=System.nanoTime();
        var graph=repository.loadGraph(owner.courseId(), owner.userId());
        assertEquals(2000, graph.concepts().size());
        var result=new NextConceptEngine().rank(graph,10);
        assertEquals(10,result.concepts().size());
        System.out.println("Graph fixture: 2000 independent nodes, read+rankingMs="+(System.nanoTime()-started)/1_000_000);
        concept(owner, "Too many", 10);
        var error = assertThrows(ResponseStatusException.class, () -> repository.loadGraph(owner.courseId(), owner.userId()));
        assertEquals(422, error.getStatusCode().value());
    }

    @Test void acceptsEdgeCapAndRejectsCapPlusOne() throws Exception {
        var owner = owner(); batchConcepts(owner, 102);
        var ids = jdbc.queryForList("select id from concepts where course_id=? order by id", Long.class, owner.courseId());
        var pairs = new ArrayList<Object[]>();
        for (int i = 0; i < 100; i++) for (int j = 0; j < 102; j++) {
            if (i != j && pairs.size() < 10000) pairs.add(new Object[]{ids.get(i), ids.get(j)});
        }
        jdbc.batchUpdate("insert into concept_dependencies(concept_id,prerequisite_id) values (?,?)", pairs);
        assertEquals(10000, repository.loadGraph(owner.courseId(), owner.userId()).edges().size());
        edge(ids.get(101), ids.getFirst());
        assertEquals(422, assertThrows(ResponseStatusException.class,
            () -> repository.loadGraph(owner.courseId(), owner.userId())).getStatusCode().value());
    }

    @Test void graphAndEvidenceUseFourJdbcQueriesRegardlessOfNodeCount() throws Exception {
        var owner=owner();concept(owner,"Initial",20);
        var counted=new CountingDataSource(Objects.requireNonNull(jdbc.getDataSource()));
        var measuredJdbc=new JdbcTemplate(counted);
        var service=new NextConceptService(mock(ResourceAccessService.class),new NextConceptRepository(measuredJdbc),
            new NextConceptEngine(),new LearningContextRepository(measuredJdbc));
        assertEquals(1,service.recommend(owner.courseId(),owner.userId(),5).recommendations().size());
        assertEquals(4,counted.statements());
        batchConcepts(owner,100);
        counted.reset();
        assertEquals(5,service.recommend(owner.courseId(),owner.userId(),5).recommendations().size());
        assertEquals(4,counted.statements());
    }

    private void batchConcepts(Owner owner, int count) {
        var rows = new ArrayList<Object[]>();
        for (int i = 0; i < count; i++) rows.add(new Object[]{owner.courseId(), "Node " + i});
        jdbc.batchUpdate("insert into concepts(course_id,name,confidence,status) values (?,?,20,'NEW')", rows);
    }
    private void edge(long dependent, long prerequisite) {
        jdbc.update("insert into concept_dependencies(concept_id,prerequisite_id) values (?,?)", dependent, prerequisite);
    }
}
