package com.studyhub.concept.recommendation;

import static com.studyhub.concept.recommendation.NextConceptModels.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Repository
public class NextConceptRepository {
    public static final int MAX_CONCEPTS = 2000;
    public static final int MAX_EDGES = 10000;
    private final JdbcTemplate jdbc;

    public NextConceptRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public GraphSnapshot loadGraph(long courseId, long userId) {
        var nodes = jdbc.query("""
            select c.id,c.name,c.confidence from concepts c
            join courses co on co.id=c.course_id join workspaces w on w.id=co.workspace_id
            where c.course_id=? and w.owner_id=? order by c.id limit ?
            """, (r,n) -> new ConceptNode(r.getLong(1),r.getString(2),r.getInt(3)),
            courseId,userId,MAX_CONCEPTS+1);
        if (nodes.size() > MAX_CONCEPTS) throw oversized();
        // Preserve malformed cross-Course edges as blockers, without exposing their target IDs.
        var edges = jdbc.query("""
            select c.id,p.id from concept_dependencies d
            join concepts c on c.id=d.concept_id
            join courses co on co.id=c.course_id join workspaces w on w.id=co.workspace_id
            left join concepts p on p.id=d.prerequisite_id and p.course_id=c.course_id
            where c.course_id=? and w.owner_id=? order by d.id limit ?
            """, (r,n) -> new PrerequisiteEdge(r.getLong(1),r.getObject(2,Long.class)),
            courseId,userId,MAX_EDGES+1);
        if (edges.size() > MAX_EDGES) throw oversized();
        return new GraphSnapshot(nodes,edges);
    }

    private ResponseStatusException oversized() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Course graph exceeds recommendation limits");
    }
}
