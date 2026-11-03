package com.studyhub.quiz;

import java.sql.DriverManager;
import org.springframework.test.context.DynamicPropertyRegistry;

abstract class QuizReviewPostgresSupport extends QuizReviewTestSupport {
    static void database(DynamicPropertyRegistry registry,String schema) throws Exception {
        try(var c=connection();var sql=c.createStatement()) {sql.execute("CREATE SCHEMA "+schema);}
        String url=System.getenv("PGVECTOR_TEST_URL");
        registry.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+schema+",public");
        registry.add("spring.datasource.username",()->System.getenv("PGVECTOR_TEST_USER"));
        registry.add("spring.datasource.password",()->System.getenv("PGVECTOR_TEST_PASSWORD"));
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.flyway.default-schema",()->schema);
    }
    static java.sql.Connection connection() throws Exception {
        return DriverManager.getConnection(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));
    }
    static void cleanup(String schema) throws Exception {
        try(var c=connection();var sql=c.createStatement()) {sql.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
    }
}
