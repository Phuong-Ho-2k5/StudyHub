package com.studyhub.ai.quiz;

import static org.junit.jupiter.api.Assertions.*;
import static com.studyhub.ai.quiz.QuizGenerationModels.*;
import com.studyhub.quiz.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class QuizGenerationPersistenceTest {
    JdbcTemplate jdbc; QuizGenerationRepository repository; QuizGenerationSourceRepository sources;
    @BeforeEach void setup() {
        var ds=new SingleConnectionDataSource("jdbc:h2:mem:quizgen_"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","",true);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE document_chunks(id BIGINT PRIMARY KEY,document_id BIGINT,ingestion_id VARCHAR(36),chunk_index INTEGER,content TEXT,page_start INTEGER,page_end INTEGER)");
        jdbc.update("INSERT INTO users(id,email,password_hash,name) VALUES (1,'quiz@example.com','test','Owner')");
        jdbc.update("INSERT INTO workspaces(id,name,owner_id) VALUES (1,'Workspace',1)");
        jdbc.update("INSERT INTO courses(id,name,workspace_id) VALUES (1,'Course',1)");
        jdbc.update("INSERT INTO documents(id,title,course_id,file_name,file_type,storage_path) VALUES (1,'Document',1,'test.pdf','application/pdf','legacy/test.pdf')");
        jdbc.update("INSERT INTO document_ingestions(id,document_id,requested_by,status) VALUES ('version-1',1,1,'INDEXED')");
        jdbc.update("UPDATE documents SET published_ingestion_id='version-1' WHERE id=1");
        jdbc.update("INSERT INTO document_chunks VALUES (101,1,'version-1',0,'Atomicity keeps changes atomic.',1,1)");
        sources=new QuizGenerationSourceRepository(jdbc);
        repository=new QuizGenerationRepository(jdbc,new DataSourceTransactionManager(ds),new ObjectMapper(),sources);
    }
    SourceWindow window() { return sources.read(1L,List.of(1L),1L,0,10); }
    ValidatedResult result(String text) {
        return new QuizGenerationEvidenceValidator().validate(window(),new GeneratedResult("model",PROMPT_VERSION,List.of(
            new GeneratedQuestion(text,List.of(new Option("A",true),new Option("B",false),new Option("C",false),new Option("D",false)),List.of(new Evidence(101L,"Atomicity"))))),1);
    }
    @Test void factoryAlwaysCreatesAiDraft() {
        var quiz=Quiz.aiGenerated("AI",null);
        assertEquals(QuizStatus.DRAFT,quiz.getStatus());assertEquals(QuizSourceType.AI_GENERATED,quiz.getSourceType());
    }
    @Test void savesCompleteDraftAndReplaySurvivesEditsAndDeletion() {
        var saved=repository.saveDraft(window(),result("What is atomic?"),1L,"key","hash","Revision");
        assertEquals("DRAFT",saved.quiz().status().name());assertEquals(4,saved.questions().getFirst().options().size());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM quiz_question_evidence",Integer.class));
        jdbc.update("UPDATE quizzes SET title='Edited' WHERE id=?",saved.quiz().id());
        assertEquals(saved,repository.findCompleted(1L,"key","hash").orElseThrow());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->repository.findCompleted(1L,"key","other")).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->repository.getGeneration(saved.quiz().id(),2L)).getStatusCode().value());
        jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=1");
        assertFalse(repository.getGeneration(saved.quiz().id(),1L).sourceCurrent());
        jdbc.update("DELETE FROM quizzes WHERE id=?",saved.quiz().id());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->repository.findCompleted(1L,"key","hash")).getStatusCode().value());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM quiz_question_evidence",Integer.class));
    }
    @Test void staleSourcesAndPersistenceErrorsLeaveNoPartialDraft() {
        var w=window();var valid=result("Question");
        jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=1");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->repository.saveDraft(w,valid,1L,"k","h","Quiz")).getStatusCode().value());
        jdbc.update("UPDATE documents SET published_ingestion_id='version-1' WHERE id=1");
        var broken=new ValidatedResult("model",PROMPT_VERSION,List.of(valid.questions().getFirst(),new ValidatedQuestion("x".repeat(501),valid.questions().getFirst().options(),valid.questions().getFirst().citations())));
        assertThrows(RuntimeException.class,()->repository.saveDraft(w,broken,1L,"k","h","Quiz"));
        for(String table:List.of("quizzes","questions","answer_options","quiz_generations","quiz_question_evidence"))
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class),table);
    }
    @Test void missingPublishedChunksIsConflictButOutsideWindowIsUnprocessable() {
        assertEquals(422,assertThrows(ResponseStatusException.class,()->sources.read(1L,List.of(1L),1L,100,10)).getStatusCode().value());
        jdbc.update("DELETE FROM document_chunks");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->sources.read(1L,List.of(1L),1L,0,10)).getStatusCode().value());
    }
}
