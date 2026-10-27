package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class DocumentIngestionQueueTest {
    JdbcTemplate jdbc;
    DocumentIngestionRepository queue;
    TransactionTemplate tx;
    long documentId;
    long userId;
    com.zaxxer.hikari.HikariDataSource ds;

    @BeforeEach void setup() {
        String url="jdbc:h2:mem:queue_"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        ds=new com.zaxxer.hikari.HikariDataSource();
        ds.setJdbcUrl(url);ds.setUsername("sa");ds.setPassword("");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);
        var manager=new DataSourceTransactionManager(ds);
        tx=new TransactionTemplate(manager);
        queue=new DocumentIngestionRepository(jdbc,manager,600,240,3);
        jdbc.update("INSERT INTO users(email,password_hash,name) VALUES ('queue@test.com','test','Owner')");
        userId=jdbc.queryForObject("SELECT id FROM users",Long.class);
        jdbc.update("INSERT INTO workspaces(name,owner_id) VALUES ('Workspace',?)",userId);
        jdbc.update("INSERT INTO courses(name,workspace_id) SELECT 'Course',id FROM workspaces");
        jdbc.update("INSERT INTO documents(course_id,title,file_name,file_type,storage_path,managed_file) SELECT id,'PDF','lesson.pdf','application/pdf','key.pdf',true FROM courses");
        documentId=jdbc.queryForObject("SELECT id FROM documents",Long.class);
    }
    @AfterEach void close(){if(ds!=null)ds.close();}

    @Test void enqueueParticipatesInUploadTransactionAndRollsBack() {
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
            queue.enqueueUploaded(documentId,userId);
            throw new IllegalStateException("rollback upload");
        }));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM document_ingestions",Integer.class));
        assertEquals("NOT_REQUESTED",state());
        tx.executeWithoutResult(status->queue.enqueueUploaded(documentId,userId));
        assertEquals("PENDING",state());
        assertNotNull(queue.claimNext());
        assertEquals("PROCESSING",state());
        assertNull(queue.claimNext());
    }

    @Test void wrongOwnerCannotCreateOrClaimWork() {
        var error=assertThrows(ResponseStatusException.class,()->queue.claimNow(documentId,-1L));
        assertEquals(404,error.getStatusCode().value());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM document_ingestions",Integer.class));
    }

    @Test void concurrentManualClaimsHaveOneWinner() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> work=()->{start.await();try{queue.claimNow(documentId,userId);return 200;}
                catch(ResponseStatusException e){return e.getStatusCode().value();}};
            var first=pool.submit(work);var second=pool.submit(work);start.countDown();
            var results=new ArrayList<>(List.of(first.get(5,TimeUnit.SECONDS),second.get(5,TimeUnit.SECONDS)));
            Collections.sort(results);assertEquals(List.of(200,409),results);
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM document_ingestions",Integer.class));
    }

    @Test void expiredClaimIsRecoveredAndOldWorkerIsFenced() {
        var old=queue.claimNow(documentId,userId);
        expire(old.id());
        var recovered=queue.claimNext();
        assertEquals(old.id(),recovered.id());
        assertNotEquals(old.token(),recovered.token());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->queue.renew(old)).getStatusCode().value());
        queue.fail(old,"INGESTION_FAILED");
        assertEquals("PROCESSING",state());
        queue.renew(recovered);
        queue.fail(recovered,"AI_UNAVAILABLE");
        assertEquals("FAILED",state());
        assertEquals("AI_UNAVAILABLE",jdbc.queryForObject("SELECT ingestion_error FROM documents",String.class));
        assertNotEquals(old.id(),queue.claimNow(documentId,userId).id());
    }

    @Test void repeatedCrashRecoveryHasBoundedAttemptsAndManualRetryIsAllowed() {
        var claim=queue.claimNow(documentId,userId);
        for(int i=0;i<2;i++){expire(claim.id());claim=queue.claimNext();assertNotNull(claim);}
        expire(claim.id());
        assertNull(queue.claimNext());
        assertEquals("FAILED",state());
        assertEquals("INTERRUPTED",jdbc.queryForObject("SELECT ingestion_error FROM documents",String.class));
        assertNotNull(queue.claimNow(documentId,userId));
    }

    @Test void deletedDocumentCancelsQueuedWorkAndStaleFailureDoesNotResurrectIt() {
        var claim=queue.claimNow(documentId,userId);
        jdbc.update("DELETE FROM documents WHERE id=?",documentId);
        queue.fail(claim,"INGESTION_FAILED");
        assertNull(queue.claimNext());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM document_ingestions",Integer.class));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->queue.renew(claim)).getStatusCode().value());
    }

    String state(){return jdbc.queryForObject("SELECT ingestion_status FROM documents WHERE id=?",String.class,documentId);}
    void expire(String id){jdbc.update("UPDATE document_ingestions SET lease_until=TIMESTAMP '2000-01-01 00:00:00' WHERE id=?",id);}
}
