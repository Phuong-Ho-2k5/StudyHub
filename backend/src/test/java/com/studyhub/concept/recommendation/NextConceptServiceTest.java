package com.studyhub.concept.recommendation;

import static com.studyhub.concept.recommendation.NextConceptModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.studyhub.ai.advice.LearningContextRepository;
import com.studyhub.security.ResourceAccessService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class NextConceptServiceTest {
    private final ResourceAccessService access=mock(ResourceAccessService.class);
    private final NextConceptRepository repository=mock(NextConceptRepository.class);
    private final LearningContextRepository learning=mock(LearningContextRepository.class);
    private final Instant now=Instant.parse("2026-11-06T00:00:00Z");
    private final NextConceptService service=new NextConceptService(access,repository,new NextConceptEngine(),learning,
        Clock.fixed(now,ZoneOffset.UTC));

    @Test void checksOwnershipBeforeAnyDataRead() {
        when(access.requireCourse(1L,2L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.recommend(1,2,5)).getStatusCode().value());
        verifyNoInteractions(repository,learning);
    }

    @Test void mapsEvidenceReferencesAndMissingActivityWithoutChangingRanking() {
        when(repository.loadGraph(1,2)).thenReturn(new GraphSnapshot(List.of(
            new ConceptNode(10,"Foundation",20),new ConceptNode(11,"Next",10)),List.of(new PrerequisiteEdge(11,10L))));
        when(learning.attempts(1,2)).thenReturn(List.of());
        when(learning.study(1,2)).thenReturn(new LearningContextRepository.StudySummary(0,0,null));
        var result=service.recommend(1,2,5);
        assertEquals(now.toString(),result.asOf());
        assertEquals("next-concepts-v1",result.algorithmVersion());
        assertEquals("READY",result.status());
        var item=result.recommendations().getFirst();
        assertEquals(10,item.conceptId()); assertEquals(1,item.rank());
        assertEquals("REVIEW",item.action());
        assertEquals(List.of("LOW_CONFIDENCE","FOUNDATION_FOR_OTHER_CONCEPTS"),item.reasonCodes());
        assertEquals(List.of("concept:10"),item.learningEvidenceIds());
        var fact=result.learningEvidence().stream().filter(f->f.id().equals("concept:10")).findFirst().orElseThrow();
        assertEquals(true,fact.data().get("prerequisitesSatisfied"));
        assertEquals(80,fact.data().get("readinessThreshold"));
        assertEquals(1,fact.data().get("unlocksCount"));
        assertEquals(List.of("NO_QUIZ_ATTEMPTS","NO_COMPLETED_STUDY_SESSIONS"),result.limitations());
        var order=inOrder(access,repository,learning);
        order.verify(access).requireCourse(1L,2L); order.verify(repository).loadGraph(1,2);
        order.verify(learning).attempts(1,2); order.verify(learning).study(1,2);
    }

    @Test void courseActivityIsNotAttributedToIndividualConcepts() {
        when(repository.loadGraph(1,2)).thenReturn(new GraphSnapshot(List.of(new ConceptNode(10,"Developing",50)),List.of()));
        when(learning.attempts(1,2)).thenReturn(List.of(new LearningContextRepository.AttemptRow(7,8,20,2,10,now.toString())));
        when(learning.study(1,2)).thenReturn(new LearningContextRepository.StudySummary(1,120,now.toString()));
        var result=service.recommend(1,2,1);
        assertEquals("CONTINUE",result.recommendations().getFirst().action());
        assertEquals(List.of("DEVELOPING_CONFIDENCE"),result.recommendations().getFirst().reasonCodes());
        assertEquals(List.of("concept:10"),result.recommendations().getFirst().learningEvidenceIds());
        assertEquals(List.of("concept:10","attempt:7","study:summary"),result.learningEvidence().stream().map(f->f.id()).toList());
        assertEquals(120L,result.learningEvidence().getLast().data().get("totalStudySeconds"));
        assertTrue(result.limitations().isEmpty());
    }
}
