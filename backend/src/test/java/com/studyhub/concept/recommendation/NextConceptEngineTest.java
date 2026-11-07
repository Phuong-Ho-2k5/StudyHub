package com.studyhub.concept.recommendation;

import static com.studyhub.concept.recommendation.NextConceptModels.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class NextConceptEngineTest {
    private final NextConceptEngine engine = new NextConceptEngine();

    @Test void prerequisitesComeBeforeDependents() {
        var edges = List.of(e(2, 1L), e(3, 2L));
        assertEquals(List.of(1L), ids(rank(List.of(n(1,20), n(2,40), n(3,10)), edges)));
        assertEquals(List.of(2L), ids(rank(List.of(n(1,80), n(2,40), n(3,10)), edges)));
        assertEquals(List.of(3L), ids(rank(List.of(n(1,80), n(2,80), n(3,10)), edges)));
    }

    @Test void checksTransitivePrerequisitesAndEveryParent() {
        assertEquals(List.of(1L), ids(rank(List.of(n(1,20), n(2,90), n(3,10)), List.of(e(2,1L), e(3,2L)))));
        assertEquals(List.of(2L), ids(rank(List.of(n(1,80), n(2,20), n(3,10)), List.of(e(3,1L), e(3,2L)))));
    }

    @Test void ranksByGroupUnlocksConfidenceThenId() {
        var nodes = List.of(n(1,49), n(2,50), n(3,79), n(4,80), n(5,10), n(6,10), n(7,0));
        var edges = List.of(e(7,1L));
        var result = rank(nodes, edges);
        assertEquals(List.of(1L,5L,6L,2L,3L), ids(result));
        assertEquals(1, result.concepts().getFirst().unlocksCount());
        assertEquals(List.of(1L), ids(engine.rank(new GraphSnapshot(nodes,edges),1)));
        var reversed = new ArrayList<>(nodes); Collections.reverse(reversed);
        assertEquals(result, rank(reversed, edges));
    }

    @Test void blocksCyclesAndTheirDependentsButKeepsIndependentBranch() {
        var edges = List.of(e(1,2L),e(2,1L),e(3,2L));
        var result = rank(List.of(n(1,10),n(2,90),n(3,0),n(4,30)),edges);
        assertEquals(List.of(4L),ids(result));
        assertEquals(List.of("CYCLIC_PREREQUISITES"),result.limitations());
        assertEquals("READY",result.status());
        assertEquals("BLOCKED_BY_GRAPH", rank(List.of(n(1,90),n(2,90),n(3,90)),edges).status());
    }

    @Test void propagatesForeignEdgeBlockAndDoesNotCountBlockedDescendants() {
        var result = rank(List.of(n(1,20),n(2,10),n(3,10),n(4,10)),
            List.of(e(2,null),e(3,2L),e(2,1L)));
        assertEquals(List.of(4L,1L), ids(result));
        assertTrue(result.concepts().stream().allMatch(c -> c.unlocksCount()==0));
        assertEquals(List.of("INVALID_PREREQUISITE_GRAPH"),result.limitations());
    }

    @Test void countsDiamondDescendantsOnce() {
        var result = rank(List.of(n(1,10),n(2,10),n(3,90),n(4,10)),
            List.of(e(2,1L),e(3,1L),e(4,2L),e(4,3L)));
        assertEquals(List.of(1L),ids(result));
        assertEquals(2,result.concepts().getFirst().unlocksCount());
    }

    @Test void handlesLongChainWithoutRecursion() {
        var nodes = new ArrayList<ConceptNode>(); var edges = new ArrayList<PrerequisiteEdge>();
        for (long i=1;i<=2000;i++) { nodes.add(n(i,20)); if(i>1) edges.add(e(i,i-1)); }
        var result = rank(nodes,edges);
        assertEquals(List.of(1L),ids(result));
        assertEquals(1999,result.concepts().getFirst().unlocksCount());
    }

    @Test void emptyAndMasteredStatuses() {
        assertEquals("NO_CONCEPTS",rank(List.of(),List.of()).status());
        assertEquals("ALL_MASTERED",rank(List.of(n(1,80),n(2,100)),List.of(e(2,1L))).status());
    }

    @Test void handlesDenseGraphAtBothCapsDeterministically() {
        var nodes=new ArrayList<ConceptNode>();var edges=new ArrayList<PrerequisiteEdge>();
        for(long i=1;i<=2000;i++) nodes.add(n(i,20));
        for(long dependent=1001;dependent<=2000;dependent++) {
            for(long prerequisite=1;prerequisite<=10;prerequisite++) edges.add(e(dependent,prerequisite));
        }
        long started=System.nanoTime();
        var result=rank(nodes,edges);
        assertEquals(java.util.stream.LongStream.rangeClosed(1,10).boxed().toList(),ids(result));
        assertTrue(result.concepts().stream().allMatch(c->c.unlocksCount()==1000));
        System.out.println("Graph fixture: 2000 nodes/10000 edges, rankingMs="+(System.nanoTime()-started)/1_000_000);
    }

    private RankingResult rank(List<ConceptNode> nodes,List<PrerequisiteEdge> edges) {
        return engine.rank(new GraphSnapshot(nodes,edges),10);
    }
    private static ConceptNode n(long id,int confidence) {return new ConceptNode(id,"Concept "+id,confidence);}
    private static PrerequisiteEdge e(long dependent,Long prerequisite) {return new PrerequisiteEdge(dependent,prerequisite);}
    private static List<Long> ids(RankingResult result) {return result.concepts().stream().map(c->c.concept().id()).toList();}
}
