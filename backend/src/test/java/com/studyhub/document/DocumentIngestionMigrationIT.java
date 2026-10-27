package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
class DocumentIngestionMigrationIT {
    @Test void upgradesExistingVectorsWithoutPublishingMixedModelsAndQueuesUnindexedPdfs() {
        String schema="lifecycle_upgrade_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv("PGVECTOR_TEST_URL"),user=System.getenv("PGVECTOR_TEST_USER"),password=System.getenv("PGVECTOR_TEST_PASSWORD");
        var flyway=Flyway.configure().dataSource(url,user,password).defaultSchema(schema)
                .locations("classpath:db/migration","classpath:db/vendor/postgresql").load();
        Flyway.configure().configuration(flyway.getConfiguration()).target("20").load().migrate();
        var ds=new DriverManagerDataSource(url,user,password);
        var properties=new Properties();properties.setProperty("currentSchema",schema+",public");ds.setConnectionProperties(properties);
        var jdbc=new JdbcTemplate(ds);
        try {
            long owner=jdbc.queryForObject("INSERT INTO users(email,password_hash,name) VALUES ('upgrade@test.com','test','Owner') RETURNING id",Long.class);
            long workspace=jdbc.queryForObject("INSERT INTO workspaces(name,owner_id) VALUES ('Workspace',?) RETURNING id",Long.class,owner);
            long course=jdbc.queryForObject("INSERT INTO courses(name,workspace_id) VALUES ('Course',?) RETURNING id",Long.class,workspace);
            var docs=new ArrayList<Long>();
            for(int i=0;i<3;i++)docs.add(jdbc.queryForObject("INSERT INTO documents(course_id,title,file_name,file_type,storage_path,managed_file) VALUES (?,'PDF','lesson.pdf','application/pdf','key.pdf',true) RETURNING id",Long.class,course));
            for(int i=0;i<2;i++)jdbc.update("INSERT INTO document_chunks(document_id,chunk_index,content,embedding,embedding_model,embedding_dimensions) VALUES (?,0,'old','[1,0,0]'::vector,'model@rev',3)",docs.get(i));
            jdbc.update("INSERT INTO document_chunks(document_id,chunk_index,content,embedding,embedding_model,embedding_dimensions) VALUES (?,1,'mixed','[0,1,0]'::vector,'different@rev',3)",docs.get(1));
            flyway.migrate();
            assertEquals("INDEXED",jdbc.queryForObject("SELECT ingestion_status FROM documents WHERE id=?",String.class,docs.get(0)));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM document_chunks ch JOIN documents d ON d.id=ch.document_id AND d.published_ingestion_id=ch.ingestion_id WHERE d.id=?",Integer.class,docs.get(0)));
            for(long id:docs.subList(1,3)){
                assertEquals("PENDING",jdbc.queryForObject("SELECT ingestion_status FROM documents WHERE id=?",String.class,id));
                assertNull(jdbc.queryForObject("SELECT published_ingestion_id FROM documents WHERE id=?",String.class,id));
                assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM document_ingestions WHERE document_id=? AND status='PENDING'",Integer.class,id));
            }
            assertEquals(0,flyway.migrate().migrationsExecuted);
            jdbc.update("DELETE FROM users WHERE id=?",owner);
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM document_ingestions",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM document_chunks",Integer.class));
        } finally {jdbc.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
}
