package com.studyhub.quiz;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
class QuizReviewMigrationIT {
    @Test void upgradePreservesExistingDraftsAndEnforcesReviewConstraints() {
        String schema="review_upgrade_"+UUID.randomUUID().toString().replace("-","");
        var flyway=Flyway.configure().dataSource(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"))
                .defaultSchema(schema).locations("classpath:db/migration","classpath:db/vendor/postgresql").load();
        var ds=new DriverManagerDataSource(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));
        var props=new Properties();props.setProperty("currentSchema",schema+",public");ds.setConnectionProperties(props);
        var jdbc=new JdbcTemplate(ds);
        try {
            Flyway.configure().configuration(flyway.getConfiguration()).target("25").load().migrate();
            long owner=jdbc.queryForObject("INSERT INTO users(email,password_hash,name) VALUES ('review@test.local','hash','Owner') RETURNING id",Long.class);
            long workspace=jdbc.queryForObject("INSERT INTO workspaces(name,owner_id) VALUES ('Review',?) RETURNING id",Long.class,owner);
            long course=jdbc.queryForObject("INSERT INTO courses(name,workspace_id) VALUES ('Course',?) RETURNING id",Long.class,workspace);
            long quiz=jdbc.queryForObject("INSERT INTO quizzes(title,course_id,source_type) VALUES ('Existing draft',?,'AI_GENERATED') RETURNING id",Long.class,course);
            long q=jdbc.queryForObject("INSERT INTO questions(quiz_id,question_text) VALUES (?,'Existing question') RETURNING id",Long.class,quiz);
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE quizzes SET status='CONFIRMED' WHERE id=?",quiz));
            assertEquals(1,Flyway.configure().configuration(flyway.getConfiguration()).target("26").load().migrate().migrationsExecuted);
            assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM quizzes WHERE id=?",String.class,quiz));
            assertEquals(0L,jdbc.queryForObject("SELECT content_revision FROM quizzes WHERE id=?",Long.class,quiz));
            assertNull(jdbc.queryForObject("SELECT reviewed_by FROM questions WHERE id=?",Long.class,q));
            jdbc.update("UPDATE quizzes SET status='CONFIRMED',confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP WHERE id=?",owner,quiz);
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE quizzes SET source_type='MANUAL' WHERE id=?",quiz));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE quizzes SET status='UNKNOWN' WHERE id=?",quiz));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE quizzes SET content_revision=-1 WHERE id=?",quiz));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE questions SET reviewed_by=? WHERE id=?",owner,q));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE quizzes SET confirmed_at=NULL WHERE id=?",quiz));
            jdbc.update("UPDATE questions SET reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?",owner,q);
            // New reviewer foreign keys must not prevent the existing owner cascade.
            jdbc.update("DELETE FROM users WHERE id=?",owner);
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM quizzes",Integer.class));
        } finally {jdbc.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
    }
}
