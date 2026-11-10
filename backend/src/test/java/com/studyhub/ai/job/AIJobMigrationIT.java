package com.studyhub.ai.job;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
class AIJobMigrationIT {
    @Test void processingUpgradeReclaimsAndPublishesWithOldAttemptFenced() {
        String schema="processing_upgrade_"+UUID.randomUUID().toString().replace("-","");
        var flyway=Flyway.configure().dataSource(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"))
            .defaultSchema(schema).locations("classpath:db/migration","classpath:db/vendor/postgresql").load();
        var ds=new DriverManagerDataSource(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));
        var props=new Properties();props.setProperty("currentSchema",schema+",public");ds.setConnectionProperties(props);var jdbc=new JdbcTemplate(ds);
        try {
            Flyway.configure().configuration(flyway.getConfiguration()).target("28").load().migrate();
            long user=jdbc.queryForObject("insert into users(email,password_hash,name) values ('processing@test.local','hash','User') returning id",Long.class);
            long workspace=jdbc.queryForObject("insert into workspaces(name,owner_id) values ('Workspace',?) returning id",Long.class,user);
            long course=jdbc.queryForObject("insert into courses(name,workspace_id) values ('Course',?) returning id",Long.class,workspace);
            long document=jdbc.queryForObject("insert into documents(course_id,title,file_name,file_type,storage_path,managed_file) values (?,'Doc','doc.pdf','application/pdf','fixture.pdf',true) returning id",Long.class,course);
            String ingestion=UUID.randomUUID().toString(),token=UUID.randomUUID().toString();
            jdbc.update("insert into document_ingestions(id,document_id,requested_by,status,active_slot,attempts,claim_token,lease_until) values (?,?,?,'PROCESSING',1,1,?,CURRENT_TIMESTAMP - INTERVAL '1 hour')",ingestion,document,user,token);
            jdbc.update("update documents set ingestion_status='PROCESSING' where id=?",document);
            assertEquals(1,flyway.migrate().migrationsExecuted);
            var manager=new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds);
            var queue=new com.studyhub.document.DocumentIngestionRepository(jdbc,manager,600,240,3);
            var jobs=new AIJobService(new AIJobRepository(jdbc),jdbc,manager);
            UUID id=UUID.fromString(ingestion);assertEquals(AIJobStatus.PROCESSING,jobs.get(id,user).status());
            var claim=queue.claimNext();assertEquals(ingestion,claim.id());assertNotEquals(token,claim.token());
            queue.fail(new com.studyhub.document.DocumentIngestionRepository.Claim(ingestion,document,user,token),"AI_UNAVAILABLE");
            assertEquals(AIJobStatus.PROCESSING,jobs.get(id,user).status());
            var chunk=new com.studyhub.document.dto.DocumentExtractionResponse.Chunk(document,"doc.pdf",0,"Rebuilt content","Rebuilt content",List.of(),1,1,List.of(),List.of(),"",List.of(),1,"fixture");
            new com.studyhub.document.DocumentChunkRepository(jdbc,manager,queue).persist(claim,new com.studyhub.document.DocumentService.ExtractionSource(document,"doc.pdf","fixture.pdf",new byte[0]),List.of(chunk),List.of(List.of(1.0,0.0,0.0)),"fixture",3);
            assertEquals(AIJobStatus.COMPLETED,jobs.get(id,user).status());assertEquals(ingestion,jobs.get(id,user).result().id());
            assertEquals(ingestion,jdbc.queryForObject("select published_ingestion_id from documents where id=?",String.class,document));
            assertEquals("Rebuilt content",jdbc.queryForObject("select content from document_chunks where document_id=?",String.class,document));
        } finally {jdbc.execute("DROP SCHEMA "+schema+" CASCADE");}
    }

    @Test void upgradePreservesPublishedIndexAndQueueAndIsRepeatable() {
        String schema="jobs_upgrade_"+UUID.randomUUID().toString().replace("-","");
        var flyway=Flyway.configure().dataSource(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"))
            .defaultSchema(schema).locations("classpath:db/migration","classpath:db/vendor/postgresql").load();
        var ds=new DriverManagerDataSource(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));
        var props=new Properties();props.setProperty("currentSchema",schema+",public");ds.setConnectionProperties(props);var jdbc=new JdbcTemplate(ds);
        try {
            Flyway.configure().configuration(flyway.getConfiguration()).target("27").load().migrate();
            long user=jdbc.queryForObject("insert into users(email,password_hash,name) values ('jobs@test.local','hash','User') returning id",Long.class);
            long workspace=jdbc.queryForObject("insert into workspaces(name,owner_id) values ('Workspace',?) returning id",Long.class,user);
            long course=jdbc.queryForObject("insert into courses(name,workspace_id) values ('Course',?) returning id",Long.class,workspace);
            long document=jdbc.queryForObject("insert into documents(course_id,title,file_name,managed_file) values (?,'Doc','doc.pdf',true) returning id",Long.class,course);
            String version=UUID.randomUUID().toString();
            jdbc.update("insert into document_ingestions(id,document_id,requested_by,status) values (?,?,?,'INDEXED')",version,document,user);
            jdbc.update("update documents set ingestion_status='INDEXED',published_ingestion_id=? where id=?",version,document);
            jdbc.update("insert into document_chunks(document_id,ingestion_id,chunk_index,content,embedding,embedding_model,embedding_dimensions,embedding_text) values (?,?,0,'Original content','[1,0,0]'::vector,'fixture',3,'Original content')",document,version);
            jdbc.update("insert into document_ingestions(id,document_id,requested_by,status,active_slot) values (?,?,?,'PENDING',1)",UUID.randomUUID().toString(),document,user);
            var docs=jdbc.queryForList("select * from documents");var queue=jdbc.queryForList("select id,status,document_id from document_ingestions order by id");
            assertEquals(2,flyway.migrate().migrationsExecuted);assertEquals(0,flyway.migrate().migrationsExecuted);
            assertEquals(docs,jdbc.queryForList("select * from documents"));assertEquals(queue,jdbc.queryForList("select id,status,document_id from document_ingestions order by id"));
            assertEquals("Original content",jdbc.queryForObject("select content from document_chunks where document_id=?",String.class,document));
            assertEquals(version,jdbc.queryForObject("select ingestion_id from document_chunks where document_id=?",String.class,document));
            var manager=new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds);
            var upgradedQueue=new com.studyhub.document.DocumentIngestionRepository(jdbc,manager,600,240,3);
            var pending=upgradedQueue.claimNext();assertNotNull(pending);upgradedQueue.fail(pending,"AI_UNAVAILABLE");
            assertEquals("FAILED",jdbc.queryForObject("select status from ai_jobs where id=(select ai_job_id from document_ingestions where id=?)",String.class,pending.id()));
            assertEquals(version,jdbc.queryForObject("select published_ingestion_id from documents where id=?",String.class,document));
            String id=UUID.randomUUID().toString();
            jdbc.update("insert into ai_jobs(id,requested_by,course_id,document_id,type,status,created_at,updated_at) values (?,?,?,?,'INGEST_DOCUMENT','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,user,course,document);
            for(String mutation:List.of("status='BAD'","type='BAD'","document_id=NULL","status='COMPLETED'","result_id=' '","error_code='secret'"))
                assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("update ai_jobs set "+mutation+" where id=?",id));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("update ai_jobs set status='COMPLETED',started_at=created_at,finished_at=created_at,result_type='DOCUMENT_INDEX',result_id=? where id=?","\t\n\u2003",id));
            jdbc.update("delete from documents where id=?",document);assertEquals(0,jdbc.queryForObject("select count(*) from ai_jobs",Integer.class));
        } finally {jdbc.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
}
