package com.studyhub.ai.job;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

class AIJobPersistenceTest extends AIJobTestSupport {
    @Test void pendingIsDurable() {
        var id=pending(fixture());
        assertEquals("PENDING",jdbc.queryForObject("select status from ai_jobs where id=?",String.class,id.toString()));
    }
    @ParameterizedTest @ValueSource(strings={
        "status='UNKNOWN'", "type='UNKNOWN'", "document_id=NULL", "claim_token='token'",
        "started_at=CURRENT_TIMESTAMP", "finished_at=CURRENT_TIMESTAMP", "result_type='QUIZ'",
        "result_id='123'", "error_code='JOB_FAILED'", "status='PROCESSING'", "status='COMPLETED'", "status='FAILED'",
        "status='PROCESSING',claim_token='token',started_at=created_at - INTERVAL '1' SECOND",
        "status='COMPLETED',started_at=created_at,finished_at=created_at,result_type='QUIZ',result_id='123'",
        "status='COMPLETED',started_at=created_at,finished_at=created_at,result_type='DOCUMENT_INDEX',result_id=' '",
        "status='FAILED',started_at=created_at,finished_at=created_at,error_code='SECRET_EXCEPTION'"
    }) void databaseRejectsInvalidSnapshots(String mutation) {
        var id=pending(fixture());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update ai_jobs set "+mutation+" where id=?",id.toString()));
    }
    @Test void databaseRejectsWhitespaceOnlyResultReferences() {
        var id=pending(fixture());
        for(String blank:new String[]{"\t", "\n", "\u2003", "\u3000"}) {
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update ai_jobs set status='COMPLETED',started_at=created_at,finished_at=created_at,result_type='DOCUMENT_INDEX',result_id=? where id=?",blank,id.toString()));
        }
    }
    @ParameterizedTest @ValueSource(strings={"users","courses","documents"})
    void parentDeletionCascades(String table) {
        var f=fixture();var id=pending(f);
        long parent=switch(table){case "users"->f.user();case "courses"->f.course();default->f.document();};
        jdbc.update("delete from "+table+" where id=?",parent);
        assertEquals(0,jdbc.queryForObject("select count(*) from ai_jobs where id=?",Integer.class,id.toString()));
    }
}
