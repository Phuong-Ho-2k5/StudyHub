package com.studyhub.document;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Source reads share a single statement snapshot of ownership and publication. */
@Repository
public class DocumentSourceRepository {
    private final JdbcTemplate jdbc;
    public DocumentSourceRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record PublishedSource(Long courseId, Long documentId, String documentTitle, Long chunkId,
            int chunkIndex, String content, Integer pageStart, Integer pageEnd, boolean managedFile) {
        public String fileUrl() { return managedFile ? "/api/documents/"+documentId+"/file" : null; }
    }
    private static final String SELECT = """
        SELECT c.id AS course_id,d.id AS document_id,d.title,ch.id AS chunk_id,ch.chunk_index,
            ch.content,ch.page_start,ch.page_end,d.managed_file
        FROM document_chunks ch
        JOIN documents d ON d.id=ch.document_id AND d.published_ingestion_id=ch.ingestion_id
        JOIN document_ingestions i ON i.id=d.published_ingestion_id AND i.document_id=d.id AND i.status='INDEXED'
        JOIN courses c ON c.id=d.course_id
        JOIN workspaces w ON w.id=c.workspace_id
        WHERE c.id=? AND w.owner_id=?
        """;
    private List<PublishedSource> query(String suffix, Object... params) {
        return jdbc.query(SELECT+suffix,(rs,n)->new PublishedSource(rs.getLong("course_id"),
            rs.getLong("document_id"),rs.getString("title"),rs.getLong("chunk_id"),rs.getInt("chunk_index"),
            rs.getString("content"),rs.getObject("page_start",Integer.class),rs.getObject("page_end",Integer.class),
            rs.getBoolean("managed_file")),params);
    }
    public Optional<PublishedSource> findPublished(Long courseId,Long documentId,Long chunkId,Long userId) {
        return query(" AND d.id=? AND ch.id=?",courseId,userId,documentId,chunkId).stream().findFirst();
    }
    public List<PublishedSource> findPublishedByIds(Long courseId,Long userId,List<Long> chunkIds) {
        if(chunkIds.isEmpty()) return List.of();
        var params=new ArrayList<Object>(); params.add(courseId);params.add(userId);params.addAll(chunkIds);
        return query(" AND ch.id IN ("+String.join(",",Collections.nCopies(chunkIds.size(),"?"))+")",params.toArray());
    }
}
