package com.studyhub.ai.job;

import static com.studyhub.ai.job.AIJobModels.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

class AIJobLifecycleTest extends AIJobTestSupport {
    @Autowired AIJobService service;
    UUID enqueue(Fixture f) {return service.enqueue(new CreateAIJob(AIJobType.INGEST_DOCUMENT,f.course(),f.document()),f.user());}
    @Test void completionIsFencedAndTerminalSnapshotNeverChanges() {
        var f=fixture();var id=enqueue(f);assertEquals(AIJobStatus.PENDING,service.get(id,f.user()).status());
        var result=new AIJobResult(AIJobResultType.DOCUMENT_INDEX,"index-1");
        assertFalse(service.complete(new AIJobClaim(id,UUID.randomUUID()),result));
        var claim=service.claim(id).orElseThrow();
        assertEquals(AIJobStatus.PROCESSING,service.get(id,f.user()).status());assertTrue(service.claim(id).isEmpty());
        assertFalse(service.complete(new AIJobClaim(id,UUID.randomUUID()),result));
        assertThrows(IllegalArgumentException.class,()->service.complete(claim,new AIJobResult(AIJobResultType.QUIZ,"1")));
        assertTrue(service.complete(claim,result));var snapshot=service.get(id,f.user());
        assertEquals(AIJobStatus.COMPLETED,snapshot.status());assertEquals(result,snapshot.result());assertNull(snapshot.errorCode());
        assertFalse(snapshot.startedAt().isBefore(snapshot.createdAt()));assertFalse(snapshot.finishedAt().isBefore(snapshot.startedAt()));
        assertFalse(service.complete(claim,new AIJobResult(AIJobResultType.DOCUMENT_INDEX,"late")));
        assertFalse(service.fail(claim,AIJobErrorCode.JOB_FAILED));assertEquals(snapshot,service.get(id,f.user()));
    }
    @Test void failureOnlyStoresAllowlistedCode() {
        var f=fixture();var id=enqueue(f);var claim=service.claim(id).orElseThrow();
        assertTrue(service.fail(claim,AIJobErrorCode.AI_TIMEOUT));var response=service.get(id,f.user());
        assertEquals(AIJobStatus.FAILED,response.status());assertEquals(AIJobErrorCode.AI_TIMEOUT,response.errorCode());assertNull(response.result());
        assertFalse(service.fail(claim,AIJobErrorCode.JOB_FAILED));assertTrue(service.claim(id).isEmpty());
    }
    @Test void scopeIsCheckedForEnqueueReadsAndWorkerMutations() {
        var f=fixture();var other=fixture();
        missing(()->service.enqueue(new CreateAIJob(AIJobType.INGEST_DOCUMENT,f.course(),other.document()),f.user()));
        missing(()->service.enqueue(new CreateAIJob(AIJobType.GENERATE_QUIZ,f.course(),null),other.user()));
        missing(()->service.enqueue(new CreateAIJob(AIJobType.GENERATE_QUIZ,Long.MAX_VALUE,null),f.user()));
        assertEquals(0,jdbc.queryForObject("select count(*) from ai_jobs where course_id=?",Integer.class,f.course()));
        var id=enqueue(f);missing(()->service.get(id,other.user()));
        jdbc.update("update workspaces set owner_id=? where id=?",other.user(),f.workspace());
        missing(()->service.get(id,f.user()));missing(()->service.get(id,other.user()));assertTrue(service.claim(id).isEmpty());
        jdbc.update("update workspaces set owner_id=? where id=?",f.user(),f.workspace());var claim=service.claim(id).orElseThrow();
        jdbc.update("update workspaces set owner_id=? where id=?",other.user(),f.workspace());
        assertFalse(service.complete(claim,new AIJobResult(AIJobResultType.DOCUMENT_INDEX,"index")));assertFalse(service.fail(claim,AIJobErrorCode.JOB_FAILED));
        jdbc.update("delete from courses where id=?",f.course());assertFalse(service.fail(claim,AIJobErrorCode.JOB_FAILED));
    }
    @Test void callerTransactionsRollbackEnqueueAndPublicationTogether() {
        var f=fixture();UUID[] id=new UUID[1];
        tx().executeWithoutResult(status->{id[0]=enqueue(f);status.setRollbackOnly();});missing(()->service.get(id[0],f.user()));
        id[0]=enqueue(f);var claim=service.claim(id[0]).orElseThrow();
        tx().executeWithoutResult(status->{
            jdbc.update("update documents set title='Published output' where id=?",f.document());
            assertTrue(service.complete(claim,new AIJobResult(AIJobResultType.DOCUMENT_INDEX,"index")));status.setRollbackOnly();
        });
        assertEquals(AIJobStatus.PROCESSING,service.get(id[0],f.user()).status());
        assertEquals("Doc",jdbc.queryForObject("select title from documents where id=?",String.class,f.document()));
    }
    @Test void courseScopedJobsAndInputValidation() {
        var f=fixture();
        for(var type:new AIJobType[]{AIJobType.EXTRACT_CONCEPTS,AIJobType.GENERATE_QUIZ}) {
            var id=service.enqueue(new CreateAIJob(type,f.course(),null),f.user());assertNull(service.get(id,f.user()).documentId());
        }
        assertThrows(IllegalArgumentException.class,()->service.enqueue(new CreateAIJob(AIJobType.INGEST_DOCUMENT,f.course(),null),f.user()));
        for(String id:new String[]{" ","x".repeat(101)})assertThrows(IllegalArgumentException.class,()->new AIJobResult(AIJobResultType.QUIZ,id));
    }
    void missing(Runnable call) {assertEquals(404,assertThrows(ResponseStatusException.class,call::run).getStatusCode().value());}
}
