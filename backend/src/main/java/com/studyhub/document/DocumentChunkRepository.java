package com.studyhub.document;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyhub.document.dto.DocumentExtractionResponse.Chunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class DocumentChunkRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper json = new ObjectMapper();

    public DocumentChunkRepository(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactions);
    }

    public void persist(DocumentService.ExtractionSource source, Long userId, List<Chunk> chunks,
            List<List<Double>> vectors, String model, int dimensions) {
        if (chunks.size() != vectors.size() || chunks.isEmpty()) throw new IllegalArgumentException("Incomplete ingestion batch");
        // Serialize outside the lock to keep the database transaction short.
        var metadata = chunks.stream().map(chunk -> {
            try { return json.writeValueAsString(chunk); }
            catch (java.io.IOException error) { throw new IllegalArgumentException("Cannot serialize chunk provenance", error); }
        }).toList();
        transaction.executeWithoutResult(tx -> {
            var ancestors = jdbc.query("""
                    SELECT c.id AS course_id,c.workspace_id FROM documents d
                    JOIN courses c ON c.id=d.course_id WHERE d.id=?
                    """,(rs,row) -> new Ancestry(rs.getLong("course_id"),rs.getLong("workspace_id")),source.id());
            if (ancestors.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Document not found");
            var ancestry = ancestors.getFirst();
            // Cascading deletes lock ancestors before descendants. Use that same order explicitly;
            // a joined FOR UPDATE can lock the document first and deadlock against those deletes.
            var owners = jdbc.query("SELECT owner_id FROM workspaces WHERE id=? FOR UPDATE",
                    (rs,row) -> rs.getLong("owner_id"),ancestry.workspaceId());
            if (owners.isEmpty() || owners.getFirst() != userId.longValue())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Document not found");
            var courses = jdbc.queryForList("SELECT id FROM courses WHERE id=? AND workspace_id=? FOR UPDATE",
                    Long.class,ancestry.courseId(),ancestry.workspaceId());
            if (courses.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Document not found");
            // The unlocked ancestry lookup is only a hint: recheck each relationship under its lock.
            var documents = jdbc.query("""
                    SELECT file_name,storage_path,file_type,managed_file
                    FROM documents WHERE id=? AND course_id=? FOR UPDATE
                    """,(rs,row) -> new LockedDocument(rs.getString("file_name"),rs.getString("storage_path"),
                            rs.getString("file_type"),rs.getBoolean("managed_file")),source.id(),ancestry.courseId());
            if (documents.isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Document not found");
            var document = documents.getFirst();
            if (!document.managed() || !Objects.equals(document.fileName(),source.fileName())
                    || !Objects.equals(document.storagePath(),source.storagePath()) || !"application/pdf".equals(document.fileType()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Document source changed. Extract the current uploaded PDF again.");
            if (jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id=?",Long.class,source.id()) > 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Document has already been ingested.");
            for (int index=0; index<chunks.size(); index++) {
                var chunk = chunks.get(index);
                jdbc.update("""
                        INSERT INTO document_chunks(document_id,chunk_index,content,page_start,page_end,
                            embedding,embedding_model,embedding_dimensions,embedding_text,metadata)
                        VALUES (?,?,?,?,?,?::vector,?,?,?,?::jsonb)
                        """,source.id(),chunk.chunkIndex(),chunk.content(),chunk.pageStart(),chunk.pageEnd(),
                        vectors.get(index).toString(),model,dimensions,chunk.embeddingText(),metadata.get(index));
            }
        });
    }

    private record Ancestry(long courseId,long workspaceId) {}
    private record LockedDocument(String fileName,String storagePath,String fileType,boolean managed) {}
}
