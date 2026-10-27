package com.studyhub.document;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

/** Durable queue. Every mutation locks ancestors before the document and its job. */
@Repository
public class DocumentIngestionRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final long leaseSeconds;
    private final int maxAttempts;

    public record Claim(String id, Long documentId, Long userId, String token) {}
    public record LockedDocument(String fileName, String storagePath, String fileType, boolean managed) {}
    private record Job(String id,String status,int attempts,String token,Timestamp lease) {}

    public DocumentIngestionRepository(JdbcTemplate jdbc, PlatformTransactionManager manager,
            @Value("${studyhub.ingestion.lease-seconds:600}") long leaseSeconds,
            @Value("${studyhub.ai.timeout-seconds:240}") long aiTimeout,
            @Value("${studyhub.ingestion.max-attempts:3}") int maxAttempts) {
        if (aiTimeout<=0 || leaseSeconds<aiTimeout+30 || maxAttempts<1)
            throw new IllegalArgumentException("Ingestion lease must exceed AI timeout by at least 30 seconds; attempts must be positive");
        this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.leaseSeconds=leaseSeconds;this.maxAttempts=maxAttempts;
    }

    /** Called only for a newly inserted document, in the upload transaction. */
    public void enqueueUploaded(Long documentId,Long userId) {
        if(!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Upload queue insertion requires the upload transaction");
        enqueue(documentId,userId);
    }

    private String enqueue(Long documentId,Long userId) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO document_ingestions(id,document_id,requested_by,status,active_slot) VALUES (?,?,?,'PENDING',1)",id,documentId,userId);
        jdbc.update("UPDATE documents SET ingestion_status='PENDING',ingestion_error=NULL WHERE id=?",documentId);
        return id;
    }

    public Claim claimNow(Long documentId,Long userId) {
        return tx.execute(status->{
            var document=lockDocument(documentId,userId);
            if(!document.managed())throw new ResponseStatusException(HttpStatus.CONFLICT,"Upload a PDF before ingestion.");
            var active=active(documentId);
            if(active!=null && active.status().equals("PROCESSING") && active.lease().after(now()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Document ingestion is already processing.");
            if(active!=null && active.attempts()>=maxAttempts){finishFailure(active.id(),documentId,"INTERRUPTED");active=null;}
            String id=active==null?enqueue(documentId,userId):active.id();
            return claim(id,documentId,userId);
        });
    }

    public Claim claimNext() {
        var candidates=jdbc.query("""
                SELECT id,document_id,requested_by FROM document_ingestions
                WHERE status='PENDING' OR (status='PROCESSING' AND lease_until<=?)
                ORDER BY created_at,id LIMIT 20
                """,(rs,row)->new Claim(rs.getString("id"),rs.getLong("document_id"),rs.getLong("requested_by"),null),now());
        for(var candidate:candidates) {
            try {
                Claim result=tx.execute(status->{
                    lockDocument(candidate.documentId(),candidate.userId());
                    var job=job(candidate.id());
                    if(job==null || !(job.status().equals("PENDING") ||
                            (job.status().equals("PROCESSING") && !job.lease().after(now()))))return null;
                    if(job.attempts()>=maxAttempts){finishFailure(job.id(),candidate.documentId(),"INTERRUPTED");return null;}
                    return claim(job.id(),candidate.documentId(),candidate.userId());
                });
                if(result!=null)return result;
            } catch(ResponseStatusException error) {
                if(error.getStatusCode()!=HttpStatus.NOT_FOUND)throw error;
                // A concurrent cascade removed the job, or its requester no longer owns the document.
                jdbc.update("DELETE FROM document_ingestions WHERE id=? AND status IN ('PENDING','PROCESSING')",candidate.id());
            }
        }
        return null;
    }

    private Claim claim(String id,Long documentId,Long userId) {
        String token=UUID.randomUUID().toString();
        jdbc.update("""
                UPDATE document_ingestions SET status='PROCESSING',claim_token=?,lease_until=?,
                attempts=attempts+1,error_code=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """,token,Timestamp.from(Instant.now().plusSeconds(leaseSeconds)),id);
        jdbc.update("UPDATE documents SET ingestion_status='PROCESSING',ingestion_error=NULL WHERE id=?",documentId);
        return new Claim(id,documentId,userId,token);
    }

    public void renew(Claim claim) {
        tx.executeWithoutResult(status->{
            lockClaim(claim);
            jdbc.update("UPDATE document_ingestions SET lease_until=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    Timestamp.from(Instant.now().plusSeconds(leaseSeconds)),claim.id());
        });
    }

    /** Must be called inside the publication transaction; holds locks until publication commits. */
    public LockedDocument lockClaim(Claim claim) {
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Claim lock requires a transaction");
        var document=lockDocument(claim.documentId(),claim.userId());
        var job=job(claim.id());
        if(job==null || !job.status().equals("PROCESSING") || !Objects.equals(job.token(),claim.token()) || !job.lease().after(now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Ingestion claim is no longer active.");
        return document;
    }

    public void fail(Claim claim,String code) {
        try {
            tx.executeWithoutResult(status->{
                lockDocument(claim.documentId(),claim.userId());
                var job=job(claim.id());
                if(job!=null && job.status().equals("PROCESSING") && Objects.equals(job.token(),claim.token()))
                    finishFailure(claim.id(),claim.documentId(),code);
            });
        } catch(ResponseStatusException error) {
            if(error.getStatusCode()!=HttpStatus.NOT_FOUND)throw error;
        }
    }

    private void finishFailure(String id,Long documentId,String code) {
        jdbc.update("UPDATE document_ingestions SET status='FAILED',active_slot=NULL,error_code=?,claim_token=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",code,id);
        jdbc.update("UPDATE documents SET ingestion_status='FAILED',ingestion_error=? WHERE id=?",code,documentId);
    }

    private Job active(Long documentId) {
        var jobs=jdbc.query("SELECT id,status,attempts,claim_token,lease_until FROM document_ingestions WHERE document_id=? AND active_slot=1 FOR UPDATE",
                (rs,row)->new Job(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getString(4),rs.getTimestamp(5)),documentId);
        return jobs.isEmpty()?null:jobs.getFirst();
    }
    private Job job(String id) {
        var jobs=jdbc.query("SELECT id,status,attempts,claim_token,lease_until FROM document_ingestions WHERE id=? FOR UPDATE",
                (rs,row)->new Job(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getString(4),rs.getTimestamp(5)),id);
        return jobs.isEmpty()?null:jobs.getFirst();
    }

    private LockedDocument lockDocument(Long documentId,Long userId) {
        var ancestors=jdbc.query("SELECT c.id,c.workspace_id FROM documents d JOIN courses c ON c.id=d.course_id WHERE d.id=?",
                (rs,row)->new long[]{rs.getLong(1),rs.getLong(2)},documentId);
        if(ancestors.isEmpty())throw missing();
        var parent=ancestors.getFirst();
        var owners=jdbc.queryForList("SELECT owner_id FROM workspaces WHERE id=? FOR UPDATE",Long.class,parent[1]);
        if(owners.isEmpty() || !owners.getFirst().equals(userId))throw missing();
        if(jdbc.queryForList("SELECT id FROM courses WHERE id=? AND workspace_id=? FOR UPDATE",Long.class,parent[0],parent[1]).isEmpty())throw missing();
        var documents=jdbc.query("SELECT file_name,storage_path,file_type,managed_file FROM documents WHERE id=? AND course_id=? FOR UPDATE",
                (rs,row)->new LockedDocument(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4)),documentId,parent[0]);
        if(documents.isEmpty())throw missing();
        return documents.getFirst();
    }
    private static Timestamp now(){return Timestamp.from(Instant.now());}
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Document not found");}
}
