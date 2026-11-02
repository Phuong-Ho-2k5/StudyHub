package com.studyhub.ai.concept;

import java.sql.DriverManager;
import org.springframework.test.context.DynamicPropertyRegistry;

abstract class ConceptPostgresSupport extends ConceptSuggestionApiSupport {
    static void database(DynamicPropertyRegistry registry,String schema) throws Exception {
        try(var c=DriverManager.getConnection(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));var sql=c.createStatement()) {
            sql.execute("CREATE SCHEMA "+schema);
        }
        String url=System.getenv("PGVECTOR_TEST_URL");
        registry.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+schema+",public");
        registry.add("spring.datasource.username",()->System.getenv("PGVECTOR_TEST_USER"));
        registry.add("spring.datasource.password",()->System.getenv("PGVECTOR_TEST_PASSWORD"));
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.flyway.default-schema",()->schema);
    }
    static void cleanup(String schema) throws Exception {
        try(var c=DriverManager.getConnection(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));var sql=c.createStatement()) {
            sql.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");
        }
    }
    @Override void insertChunk(long document,String version) {
        jdbc.update("""
            INSERT INTO document_chunks(document_id,ingestion_id,chunk_index,content,page_start,page_end,
                embedding,embedding_model,embedding_dimensions,embedding_text,metadata)
            VALUES (?,?,0,'Atomicity nguyên tử',1,1,'[1,0,0]'::vector,'fixture',3,'Atomicity nguyên tử','{}'::jsonb)
            """,document,version);
    }
}
