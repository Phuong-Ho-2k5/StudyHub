package com.studyhub.ai.quiz;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class QuizGenerationMigrationIT extends QuizGenerationPostgresSupport {
    static final String SCHEMA="quiz_migration_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) throws Exception {database(r,SCHEMA);}
    @AfterAll static void drop() throws Exception {cleanup(SCHEMA);}
    @Test void postgresMigrationPreservesSnapshotsAfterSourceAndQuizDeletion() throws Exception {
        var f=fixture();var result=call("POST",path(f),f.token(),"migration",body(),201);long quiz=result.at("/quiz/id").asLong();
        jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=?",f.document());
        jdbc.update("DELETE FROM document_chunks WHERE document_id=?",f.document());
        assertFalse(call("GET","/api/quizzes/"+quiz+"/ai-generation",f.token(),null,null,200).path("sourceCurrent").asBoolean());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM quiz_question_evidence WHERE generation_id=?",Integer.class,result.at("/generation/id").asText()));
        call("DELETE","/api/quizzes/"+quiz,f.token(),null,null,204);
        assertNull(jdbc.queryForObject("SELECT quiz_id FROM quiz_generations WHERE user_id=?",Long.class,f.owner()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM quiz_question_evidence",Integer.class));
    }
}
