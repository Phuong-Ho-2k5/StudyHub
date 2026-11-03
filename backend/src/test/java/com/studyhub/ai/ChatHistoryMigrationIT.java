package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
class ChatHistoryMigrationIT {
    @Test void upgradesExistingCourseAndPreservesSnapshotThenCascadesUserDeletion() {
        String schema="chat_upgrade_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv("PGVECTOR_TEST_URL"),user=System.getenv("PGVECTOR_TEST_USER"),password=System.getenv("PGVECTOR_TEST_PASSWORD");
        var flyway=Flyway.configure().dataSource(url,user,password).defaultSchema(schema)
            .locations("classpath:db/migration","classpath:db/vendor/postgresql").load();
        var ds=new DriverManagerDataSource(url,user,password);
        var props=new Properties(); props.setProperty("currentSchema",schema+",public");ds.setConnectionProperties(props);
        var jdbc=new JdbcTemplate(ds);
        try {
            Flyway.configure().configuration(flyway.getConfiguration()).target("22").load().migrate();
            long owner=jdbc.queryForObject("INSERT INTO users(email,password_hash,name) VALUES ('history@test.local','hash','Owner') RETURNING id",Long.class);
            long workspace=jdbc.queryForObject("INSERT INTO workspaces(name,owner_id) VALUES ('Workspace',?) RETURNING id",Long.class,owner);
            long course=jdbc.queryForObject("INSERT INTO courses(name,workspace_id) VALUES ('Course',?) RETURNING id",Long.class,workspace);
            // Check the history upgrade itself, then apply all subsequent migrations.
            // The test must not depend on how many unrelated features are added later.
            assertEquals(1,Flyway.configure().configuration(flyway.getConfiguration()).target("23").load().migrate().migrationsExecuted);
            flyway.migrate();
            long session=jdbc.queryForObject("INSERT INTO chat_sessions(user_id,course_id,title) VALUES (?,?,'History') RETURNING id",Long.class,owner,course);
            jdbc.update("INSERT INTO chat_messages(session_id,role,content,citations_json) VALUES (?,'USER','Câu hỏi','[]')",session);
            String snapshot="[{\"chunkId\":999,\"quote\":\"Nguyên tử\",\"pageStart\":null}]";
            jdbc.update("INSERT INTO chat_messages(session_id,role,content,status,prompt_version,citations_json) VALUES (?,'ASSISTANT','Trả lời','ANSWERED','course-rag-v2',?)",session,snapshot);
            assertEquals(snapshot,jdbc.queryForObject("SELECT citations_json FROM chat_messages WHERE role='ASSISTANT'",String.class));
            assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO chat_messages(session_id,role,content,citations_json) VALUES (?,'ASSISTANT','Missing metadata','[]')",session));
            assertEquals(0,flyway.migrate().migrationsExecuted);
            jdbc.update("DELETE FROM users WHERE id=?",owner);
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM chat_sessions",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM chat_messages",Integer.class));
        } finally { jdbc.execute("DROP SCHEMA "+schema+" CASCADE"); }
    }
}
