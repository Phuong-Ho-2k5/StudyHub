package com.studyhub.ai.feedback;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
class AIFeedbackMigrationIT {
    @Test void upgradesV26PreservingHistoryAndEnforcesConstraintsAndCascades() {
        String schema="feedback_upgrade_"+UUID.randomUUID().toString().replace("-","");
        var flyway=Flyway.configure().dataSource(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"))
            .defaultSchema(schema).locations("classpath:db/migration","classpath:db/vendor/postgresql").load();
        var ds=new DriverManagerDataSource(System.getenv("PGVECTOR_TEST_URL"),System.getenv("PGVECTOR_TEST_USER"),System.getenv("PGVECTOR_TEST_PASSWORD"));
        var props=new Properties();props.setProperty("currentSchema",schema+",public");ds.setConnectionProperties(props);
        var jdbc=new JdbcTemplate(ds);
        try {
            Flyway.configure().configuration(flyway.getConfiguration()).target("26").load().migrate();
            long user=jdbc.queryForObject("insert into users(email,password_hash,name) values ('feedback@test.local','hash','User') returning id",Long.class);
            long workspace=jdbc.queryForObject("insert into workspaces(name,owner_id) values ('Workspace',?) returning id",Long.class,user);
            long course=jdbc.queryForObject("insert into courses(name,workspace_id) values ('Course',?) returning id",Long.class,workspace);
            long session=jdbc.queryForObject("insert into chat_sessions(user_id,course_id,title) values (?,?,'History') returning id",Long.class,user,course);
            String snapshot="[{\"chunkId\":999,\"quote\":\"Nguyên tử\"}]";
            long message=jdbc.queryForObject("insert into chat_messages(session_id,role,content,status,prompt_version,citations_json) values (?,'ASSISTANT','Original answer','ANSWERED','v1',?) returning id",Long.class,session,snapshot);
            assertEquals(1,Flyway.configure().configuration(flyway.getConfiguration()).target("27").load().migrate().migrationsExecuted);
            flyway.migrate();assertEquals(0,flyway.migrate().migrationsExecuted);
            assertEquals(snapshot,jdbc.queryForObject("select citations_json from chat_messages where id=?",String.class,message));
            assertEquals("Original answer",jdbc.queryForObject("select content from chat_messages where id=?",String.class,message));
            String insert="insert into ai_feedback(user_id,message_id,rating,created_at,updated_at) values (?,?,'HELPFUL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)";
            jdbc.update(insert,user,message);
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update(insert,user,message));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update ai_feedback set rating='BAD' where message_id=?",message));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update ai_feedback set user_id=? where message_id=?",Long.MAX_VALUE,message));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update ai_feedback set message_id=? where message_id=?",Long.MAX_VALUE,message));
            // Roll back each cascade so every parent path is checked against the same existing history.
            for(String table:List.of("chat_messages","chat_sessions","courses","users")) {
                long id=switch(table){case "chat_messages"->message;case "chat_sessions"->session;case "courses"->course;default->user;};
                var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
                tx.executeWithoutResult(status->{
                    jdbc.update("delete from "+table+" where id=?",id);
                    assertEquals(0,jdbc.queryForObject("select count(*) from ai_feedback",Integer.class));
                    status.setRollbackOnly();
                });
            }
        } finally {jdbc.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
}
