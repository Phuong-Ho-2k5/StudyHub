package com.studyhub.concept.recommendation;

import static com.studyhub.concept.recommendation.NextConceptModels.*;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class NextConceptEngine {
    public RankingResult rank(GraphSnapshot graph, int limit) {
        Map<Long,ConceptNode> nodes = new HashMap<>();
        Map<Long,Set<Long>> prerequisites = new HashMap<>(), dependents = new HashMap<>();
        for (var node : graph.concepts()) {
            nodes.put(node.id(),node);
            prerequisites.put(node.id(),new HashSet<>());
            dependents.put(node.id(),new HashSet<>());
        }
        if (nodes.isEmpty()) return new RankingResult("NO_CONCEPTS",List.of(),List.of());

        Set<Long> invalid = new HashSet<>();
        for (var edge : graph.edges()) {
            if (!nodes.containsKey(edge.dependentId())) continue;
            if (edge.prerequisiteId() == null || !nodes.containsKey(edge.prerequisiteId())) {
                invalid.add(edge.dependentId());
            } else {
                prerequisites.get(edge.dependentId()).add(edge.prerequisiteId());
                dependents.get(edge.prerequisiteId()).add(edge.dependentId());
            }
        }
        var queue = new ArrayDeque<>(invalid);
        while (!queue.isEmpty()) {
            for (long dependent : dependents.get(queue.remove())) {
                if (invalid.add(dependent)) queue.add(dependent);
            }
        }

        Map<Long,Integer> remaining = new HashMap<>();
        for (long id : nodes.keySet()) {
            int degree = prerequisites.get(id).size();
            remaining.put(id,degree);
            if (degree == 0) queue.add(id);
        }
        var topo = new ArrayList<Long>();
        while (!queue.isEmpty()) {
            long id = queue.remove();
            topo.add(id);
            for (long dependent : dependents.get(id)) {
                if (remaining.merge(dependent,-1,Integer::sum) == 0) queue.add(dependent);
            }
        }
        Set<Long> blocked = new HashSet<>(invalid);
        for (var entry : remaining.entrySet()) if (entry.getValue() > 0) blocked.add(entry.getKey());
        var limitations = new ArrayList<String>();
        if (topo.size() < nodes.size()) limitations.add("CYCLIC_PREREQUISITES");
        if (!invalid.isEmpty()) limitations.add("INVALID_PREREQUISITE_GRAPH");

        Map<Long,Boolean> satisfied = new HashMap<>();
        var ranked = new ArrayList<RankedConcept>();
        for (long id : topo) {
            boolean ready = !blocked.contains(id) && prerequisites.get(id).stream().allMatch(p ->
                nodes.get(p).confidence() >= READINESS_THRESHOLD && satisfied.getOrDefault(p,false));
            satisfied.put(id,ready);
            if (ready && nodes.get(id).confidence() < READINESS_THRESHOLD) {
                ranked.add(new RankedConcept(nodes.get(id),countUnmasteredDescendants(id,nodes,dependents,blocked)));
            }
        }
        ranked.sort(Comparator.comparingInt((RankedConcept c) -> c.concept().confidence() < 50 ? 0 : 1)
            .thenComparing(Comparator.comparingInt(RankedConcept::unlocksCount).reversed())
            .thenComparingInt(c -> c.concept().confidence()).thenComparingLong(c -> c.concept().id()));
        String status = !ranked.isEmpty() ? "READY" : !blocked.isEmpty() ? "BLOCKED_BY_GRAPH" : "ALL_MASTERED";
        return new RankingResult(status,ranked.stream().limit(limit).toList(),List.copyOf(limitations));
    }

    private int countUnmasteredDescendants(long id, Map<Long,ConceptNode> nodes,
            Map<Long,Set<Long>> dependents, Set<Long> blocked) {
        Set<Long> visited = new HashSet<>();
        var queue = new ArrayDeque<>(dependents.get(id));
        int count = 0;
        while (!queue.isEmpty()) {
            long descendant = queue.remove();
            if (blocked.contains(descendant) || !visited.add(descendant)) continue;
            if (nodes.get(descendant).confidence() < READINESS_THRESHOLD) count++;
            queue.addAll(dependents.get(descendant));
        }
        return count;
    }
}
