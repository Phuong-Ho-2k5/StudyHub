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
    private final DocumentIngestionRepository queue;
    private final ObjectMapper json = new ObjectMapper();

    public DocumentChunkRepository(JdbcTemplate jdbc, PlatformTransactionManager transactions, DocumentIngestionRepository queue) {
        this.jdbc=jdbc;this.transaction=new TransactionTemplate(transactions);this.queue=queue;
    }

    public void persist(DocumentIngestionRepository.Claim claim, DocumentService.ExtractionSource source,
            List<Chunk> chunks,List<List<Double>> vectors,String model,int dimensions) {
        if(!claim.documentId().equals(source.id()) || chunks.size()!=vectors.size() || chunks.isEmpty())
            throw new IllegalArgumentException("Incomplete ingestion batch");
        var metadata=chunks.stream().map(chunk->{
            if(!source.id().equals(chunk.documentId()))throw new IllegalArgumentException("Chunk belongs to another document");
            try{return json.writeValueAsString(chunk);}
            catch(java.io.IOException error){throw new IllegalArgumentException("Cannot serialize chunk provenance",error);}
        }).toList();
        transaction.executeWithoutResult(tx->{
            var document=queue.lockClaim(claim);
            if(!document.managed() || !Objects.equals(document.fileName(),source.fileName())
                    || !Objects.equals(document.storagePath(),source.storagePath()) || !"application/pdf".equals(document.fileType()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Document source changed. Extract the current uploaded PDF again.");
            // Readers continue to see the old set until this entire transaction commits.
            // A later insert failure rolls back both this deletion and the pointer switch.
            jdbc.update("DELETE FROM document_chunks WHERE document_id=?",source.id());
            for(int index=0;index<chunks.size();index++){
                var chunk=chunks.get(index);
                jdbc.update("""
                        INSERT INTO document_chunks(document_id,chunk_index,content,page_start,page_end,
                            embedding,embedding_model,embedding_dimensions,embedding_text,metadata,ingestion_id)
                        VALUES (?,?,?,?,?,?::vector,?,?,?,?::jsonb,?)
                        """,source.id(),chunk.chunkIndex(),chunk.content(),chunk.pageStart(),chunk.pageEnd(),
                        vectors.get(index).toString(),model,dimensions,chunk.embeddingText(),metadata.get(index),claim.id());
            }
            jdbc.update("""
                    UPDATE document_ingestions SET status='INDEXED',active_slot=NULL,claim_token=NULL,lease_until=NULL,
                        error_code=NULL,embedding_model=?,embedding_dimensions=?,chunk_count=?,updated_at=CURRENT_TIMESTAMP WHERE id=?
                    """,model,dimensions,chunks.size(),claim.id());
            jdbc.update("""
                    UPDATE documents SET published_ingestion_id=?,ingestion_status='INDEXED',ingestion_error=NULL,
                        updated_at=CURRENT_TIMESTAMP WHERE id=?
                    """,claim.id(),source.id());
        });
    }
}
