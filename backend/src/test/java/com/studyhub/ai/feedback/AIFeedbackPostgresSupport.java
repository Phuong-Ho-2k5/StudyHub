package com.studyhub.ai.feedback;

import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AIFeedbackPostgresSupport extends AIFeedbackTestSupport {
    static final String SCHEMA="feedback_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) throws Exception {
        try(var c=DriverManager.getConnection(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));var s=c.createStatement()) {
            s.execute("CREATE SCHEMA "+SCHEMA);
        }
        String url=System.getenv("PGVECTOR_TEST_URL");
        r.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+SCHEMA+",public");
        r.add("spring.datasource.username",()->System.getenv("PGVECTOR_TEST_USER"));
        r.add("spring.datasource.password",()->System.getenv("PGVECTOR_TEST_PASSWORD"));
        r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        r.add("spring.flyway.default-schema",()->SCHEMA);
    }
    @AfterAll static void cleanup() throws Exception {
        try(var c=DriverManager.getConnection(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));var s=c.createStatement()) {
            s.execute("DROP SCHEMA "+SCHEMA+" CASCADE");
        }
    }
}
