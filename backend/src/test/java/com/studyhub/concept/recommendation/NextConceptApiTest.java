package com.studyhub.concept.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:recommendations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "studyhub.ai.token=", "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
class NextConceptApiTest extends RecommendationTestSupport {
    private String path(long courseId) {return "/api/courses/"+courseId+"/recommendations/next-concepts";}

    @Test void authenticatesScopesAndValidatesLimit() throws Exception {
        var owner=owner(); var foreign=owner(); String path=path(owner.courseId());
        assertEquals(401,call("GET",path,null,null).statusCode());
        assertEquals(404,call("GET",path,foreign.token(),null).statusCode());
        assertEquals(404,call("GET",path(Long.MAX_VALUE),owner.token(),null).statusCode());
        for(String limit:List.of("0","11","text")) assertEquals(400,call("GET",path+"?limit="+limit,owner.token(),null).statusCode());
        for(String limit:List.of("1","10")) assertEquals(200,call("GET",path+"?limit="+limit,owner.token(),null).statusCode());
        var empty=body(call("GET",path,owner.token(),null));
        assertEquals("NO_CONCEPTS",empty.path("status").asText());
        assertEquals("next-concepts-v1",empty.path("algorithmVersion").asText());
        for(int i=0;i<6;i++) concept(owner,"Independent "+i,10);
        assertEquals(5,body(call("GET",path,owner.token(),null)).path("recommendations").size());
        assertEquals(1,body(call("GET",path+"?limit=1",owner.token(),null)).path("recommendations").size());
    }

    @Test void followsPrerequisiteFrontierAndIgnoresStatus() throws Exception {
        var owner=owner(); long a=concept(owner,"A",20),b=concept(owner,"B",40),c=concept(owner,"C",10);
        edge(b,a);edge(c,b);
        jdbc.update("update concepts set status='MASTERED' where id=?",a);
        assertEquals(a,first(owner));
        jdbc.update("update concepts set confidence=80 where id=?",a);
        assertEquals(b,first(owner));
        jdbc.update("update concepts set confidence=80 where id=?",b);
        assertEquals(c,first(owner));
        jdbc.update("update concepts set confidence=80 where id=?",c);
        assertEquals("ALL_MASTERED",body(call("GET",path(owner.courseId()),owner.token(),null)).path("status").asText());
    }

    @Test void handlesCyclesAndMalformedCrossCourseEdgesWithoutLeakingData() throws Exception {
        var owner=owner();var foreign=owner();
        long a=concept(owner,"A",20),b=concept(owner,"B",20),c=concept(owner,"C",10);
        edge(a,b);edge(b,a);edge(c,b);
        assertEquals("BLOCKED_BY_GRAPH",body(call("GET",path(owner.courseId()),owner.token(),null)).path("status").asText());
        long d=concept(owner,"D",30);
        assertEquals(d,first(owner));
        long secret=concept(foreign,"SECRET CONCEPT",10); edge(d,secret);
        var result=call("GET",path(owner.courseId()),owner.token(),null);
        assertEquals(200,result.statusCode(),result.body());
        assertEquals("BLOCKED_BY_GRAPH",body(result).path("status").asText());
        assertFalse(result.body().contains("SECRET CONCEPT"));
        assertTrue(body(result).path("limitations").toString().contains("INVALID_PREREQUISITE_GRAPH"));
    }

    @Test void returnsScopedEvidenceAndDoesNotMutateLearningData() throws Exception {
        var owner=owner();var foreign=owner();concept(owner,"Local",20);
        long workspace=jdbc.queryForObject("select workspace_id from courses where id=?",Long.class,owner.courseId());
        jdbc.update("insert into courses(workspace_id,name) values (?,'Sibling evidence')",workspace);
        long siblingId=jdbc.queryForObject("select id from courses where workspace_id=? and name='Sibling evidence'",Long.class,workspace);
        var sibling=new Owner(owner.token(),owner.userId(),siblingId);
        concept(sibling,"SIBLING SECRET",1); attempt(sibling,owner.userId(),99);
        var allowed=new ArrayList<Long>();
        for(int i=0;i<6;i++) allowed.add(attempt(owner,owner.userId(),i));
        jdbc.update("update quiz_attempts set created_at=TIMESTAMP '2026-11-01 00:00:00' where user_id=?",owner.userId());
        long forbidden=attempt(owner,foreign.userId(),90);attempt(foreign,owner.userId(),95);
        jdbc.update("insert into study_sessions(user_id,course_id,start_time,end_time,duration_seconds) values (?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,120)",owner.userId(),owner.courseId());
        jdbc.update("insert into study_sessions(user_id,course_id,start_time) values (?,?,CURRENT_TIMESTAMP)",owner.userId(),owner.courseId());
        jdbc.update("insert into study_sessions(user_id,course_id,start_time,end_time,duration_seconds) values (?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,900)",foreign.userId(),owner.courseId());
        var before=snapshot(owner);
        var response=call("GET",path(owner.courseId()),owner.token(),null);
        assertEquals(200,response.statusCode(),response.body());
        var result=body(response);var facts=result.path("learningEvidence");
        var attempts=new ArrayList<Long>();var evidenceIds=new HashSet<String>();
        for(var fact:facts) {
            evidenceIds.add(fact.path("id").asText());
            if(fact.path("type").asText().equals("QUIZ_ATTEMPT_SUMMARY")) attempts.add(fact.path("data").path("id").asLong());
            if(fact.path("id").asText().equals("study:summary")) {
                assertEquals(120,fact.path("data").path("totalStudySeconds").asLong());
                assertEquals(1,fact.path("data").path("completedStudySessions").asLong());
            }
        }
        Collections.reverse(allowed);
        assertEquals(allowed.subList(0,5),attempts);assertFalse(attempts.contains(forbidden));
        assertFalse(response.body().contains("SIBLING SECRET"));
        assertEquals(facts.size(),evidenceIds.size());
        for(var item:result.path("recommendations")) for(var ref:item.path("learningEvidenceIds")) {
            assertTrue(ref.asText().startsWith("concept:"));assertTrue(evidenceIds.contains(ref.asText()));
        }
        assertEquals(before,snapshot(owner));
    }

    @Test void rejectsGraphOverflowUsingApiErrorEnvelopeAndDocumentsContract() throws Exception {
        var owner=owner();var rows=new ArrayList<Object[]>();
        for(int i=0;i<2001;i++) rows.add(new Object[]{owner.courseId(),"Node "+i});
        jdbc.batchUpdate("insert into concepts(course_id,name,confidence,status) values (?,?,0,'NEW')",rows);
        var response=call("GET",path(owner.courseId()),owner.token(),null);
        assertEquals(422,response.statusCode(),response.body());
        assertEquals("Course graph exceeds recommendation limits",body(response).path("message").asText());
        var docs=call("GET","/v3/api-docs",owner.token(),null);
        assertEquals(200,docs.statusCode());
        var operation=body(docs).path("paths").path("/api/courses/{courseId}/recommendations/next-concepts").path("get");
        assertTrue(operation.path("responses").has("422"));
        assertFalse(operation.path("parameters").isEmpty());
    }

    private long first(Owner owner) throws Exception {
        var response=call("GET",path(owner.courseId()),owner.token(),null);
        assertEquals(200,response.statusCode(),response.body());
        return body(response).path("recommendations").get(0).path("conceptId").asLong();
    }
    private void edge(long dependent,long prerequisite) {jdbc.update("insert into concept_dependencies(concept_id,prerequisite_id) values (?,?)",dependent,prerequisite);}
    private List<Object> snapshot(Owner owner) {
        return List.of(jdbc.queryForList("select id,confidence,status from concepts where course_id=? order by id",owner.courseId()),
            jdbc.queryForObject("select count(*) from quiz_attempts",Long.class),jdbc.queryForObject("select count(*) from study_sessions",Long.class),
            jdbc.queryForObject("select count(*) from chat_messages",Long.class));
    }
}
