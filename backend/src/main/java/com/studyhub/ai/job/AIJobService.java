package com.studyhub.ai.job;

import static com.studyhub.ai.job.AIJobModels.*;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Internal lifecycle only. Transactions join the caller's enqueue/publication transaction. */
@Service
public class AIJobService {
    private final AIJobRepository jobs;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String userLock;
    public AIJobService(AIJobRepository jobs,JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.jobs=jobs;this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);
        boolean postgres=Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)
            c->c.getMetaData().getDatabaseProductName().equals("PostgreSQL")));
        userLock=postgres?" FOR KEY SHARE":" FOR UPDATE";
    }
    public UUID enqueue(CreateAIJob command,Long userId) {
        if(command==null || command.type()==null || command.courseId()==null || userId==null ||
            (command.type()==AIJobType.INGEST_DOCUMENT && command.documentId()==null))
            throw new IllegalArgumentException("Job type, course and requester are required; ingestion requires a document");
        return tx.execute(status->{
            if(!lockScope(userId,command.courseId(),command.documentId()))throw missing();
            UUID id=UUID.randomUUID();jobs.insert(id,command,userId);return id;
        });
    }
    public Optional<AIJobClaim> claim(UUID jobId) {
        Objects.requireNonNull(jobId);
        return tx.execute(status->{
            var job=locked(jobId);
            if(job.isEmpty() || job.get().status()!=AIJobStatus.PENDING)return Optional.empty();
            UUID token=UUID.randomUUID();
            return jobs.claim(jobId,token)?Optional.of(new AIJobClaim(jobId,token)):Optional.empty();
        });
    }
    /** False MUST cause the caller to roll back any output publication in its transaction. */
    public boolean complete(AIJobClaim claim,AIJobResult result) {
        Objects.requireNonNull(result);
        return finish(claim,result,null);
    }
    public boolean fail(AIJobClaim claim,AIJobErrorCode code) {
        Objects.requireNonNull(code);
        return finish(claim,null,code);
    }
    private boolean finish(AIJobClaim claim,AIJobResult result,AIJobErrorCode code) {
        Objects.requireNonNull(claim);Objects.requireNonNull(claim.jobId());Objects.requireNonNull(claim.token());
        return Boolean.TRUE.equals(tx.execute(status->{
            var found=locked(claim.jobId());
            if(found.isEmpty())return false;
            var job=found.get();
            if(job.status()!=AIJobStatus.PROCESSING || !claim.token().toString().equals(job.claimToken()))return false;
            if(result!=null && result.type()!=expectedResult(job.type()))
                throw new IllegalArgumentException("Result type does not match job type");
            return jobs.finish(claim,result,code);
        }));
    }
    public AIJobResponse get(UUID jobId,Long userId) {
        Objects.requireNonNull(jobId);Objects.requireNonNull(userId);
        return AIJobResponse.from(jobs.findOwned(jobId,userId).orElseThrow(AIJobService::missing));
    }
    private Optional<Snapshot> locked(UUID id) {
        var candidate=jobs.find(id);
        if(candidate.isEmpty())return Optional.empty();
        var job=candidate.get();
        if(!lockScope(job.requestedBy(),job.courseId(),job.documentId()))return Optional.empty();
        // Lock the child only after all FK ancestors; re-read after a concurrent cascade.
        return jobs.lock(id).filter(current->current.requestedBy()==job.requestedBy() &&
            current.courseId()==job.courseId() && Objects.equals(current.documentId(),job.documentId()));
    }
    private boolean lockScope(long user,long course,Long document) {
        if(jdbc.queryForList("SELECT id FROM users WHERE id=?"+userLock,Long.class,user).isEmpty())return false;
        var parents=jdbc.queryForList("SELECT workspace_id FROM courses WHERE id=?",Long.class,course);
        if(parents.isEmpty())return false;
        long workspace=parents.getFirst();
        var owners=jdbc.queryForList("SELECT owner_id FROM workspaces WHERE id=? FOR UPDATE",Long.class,workspace);
        if(owners.isEmpty() || owners.getFirst()!=user)return false;
        if(jdbc.queryForList("SELECT id FROM courses WHERE id=? AND workspace_id=? FOR UPDATE",Long.class,course,workspace).isEmpty())return false;
        return document==null || !jdbc.queryForList("SELECT id FROM documents WHERE id=? AND course_id=? FOR UPDATE",Long.class,document,course).isEmpty();
    }
    private static AIJobResultType expectedResult(AIJobType type) {
        return switch(type) {
            case INGEST_DOCUMENT->AIJobResultType.DOCUMENT_INDEX;
            case EXTRACT_CONCEPTS->AIJobResultType.CONCEPT_SUGGESTION_BATCH;
            case GENERATE_QUIZ->AIJobResultType.QUIZ;
        };
    }
    private static ResponseStatusException missing() {return new ResponseStatusException(HttpStatus.NOT_FOUND,"AI job not found");}
}
