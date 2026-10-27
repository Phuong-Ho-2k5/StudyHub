package com.studyhub.search;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourseSearchRepository {
    private final JdbcTemplate jdbc;
    public CourseSearchRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public record SearchHit(Long chunkId,Long documentId,String documentTitle,int chunkIndex,
            String content,Integer pageStart,Integer pageEnd,double score) {}

    public List<SearchHit> search(Long courseId,Long userId,List<Double> queryVector,String model,int dimensions,int topK) {
        String vector=queryVector.toString();
        // MATERIALIZED prevents the distance operator from being evaluated on incompatible rows.
        // One statement sees a consistent published pointer and matching chunks during a rebuild.
        return jdbc.query("""
                WITH candidates AS MATERIALIZED (
                    SELECT ch.id,ch.document_id,d.title,ch.chunk_index,ch.content,ch.page_start,ch.page_end,ch.embedding
                    FROM document_chunks ch
                    JOIN documents d ON d.id=ch.document_id AND d.published_ingestion_id=ch.ingestion_id
                    JOIN document_ingestions i ON i.id=d.published_ingestion_id AND i.document_id=d.id AND i.status='INDEXED'
                    JOIN courses c ON c.id=d.course_id
                    JOIN workspaces w ON w.id=c.workspace_id
                    WHERE c.id=? AND w.owner_id=? AND ch.embedding_model=? AND ch.embedding_dimensions=?
                        AND i.embedding_model=? AND i.embedding_dimensions=?
                )
                SELECT id,document_id,title,chunk_index,content,page_start,page_end,
                    1-(embedding <=> ?::vector) AS score
                FROM candidates ORDER BY embedding <=> ?::vector,id LIMIT ?
                """,(rs,row)->new SearchHit(rs.getLong("id"),rs.getLong("document_id"),rs.getString("title"),
                        rs.getInt("chunk_index"),rs.getString("content"),rs.getObject("page_start",Integer.class),
                        rs.getObject("page_end",Integer.class),rs.getDouble("score")),
                courseId,userId,model,dimensions,model,dimensions,vector,vector,topK);
    }
}
