package com.studyhub.ai.concept;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import tools.jackson.databind.ObjectMapper;
import com.studyhub.concept.ConceptService;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.web.server.ResponseStatusException;
import static com.studyhub.ai.concept.ConceptSuggestionModels.*;

class ConceptSuggestionPersistenceTest {
    JdbcTemplate jdbc; ConceptSuggestionRepository repository;
    @BeforeEach void setup() {
        var ds=new SingleConnectionDataSource("jdbc:h2:mem:concept_"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","",true);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE document_chunks(id BIGINT PRIMARY KEY,document_id BIGINT,ingestion_id VARCHAR(36),chunk_index INTEGER,content TEXT,page_start INTEGER,page_end INTEGER)");
        jdbc.update("INSERT INTO users(id,email,password_hash,name) VALUES (1,'concept@example.com','test','Owner')");
        jdbc.update("INSERT INTO workspaces(id,name,owner_id) VALUES (1,'Workspace',1)");
        jdbc.update("INSERT INTO courses(id,name,workspace_id) VALUES (1,'Course',1)");
        jdbc.update("INSERT INTO documents(id,course_id,title,file_name,managed_file) VALUES (1,1,'Database','db.pdf',true)");
        jdbc.update("INSERT INTO document_ingestions(id,document_id,requested_by,status) VALUES ('version-1',1,1,'INDEXED')");
        jdbc.update("UPDATE documents SET published_ingestion_id='version-1' WHERE id=1");
        jdbc.update("INSERT INTO document_chunks VALUES (101,1,'version-1',0,'Nguyên tử Atomicity',1,1),(102,1,'version-1',1,'Isolation',2,2)");
        repository=new ConceptSuggestionRepository(jdbc,new DataSourceTransactionManager(ds),new ObjectMapper(),mock(ConceptService.class));
    }
    ValidatedExtraction extraction(SourceWindow window) {
        return new ConceptEvidenceValidator().validate(window,new ExtractionResult("model","document-concepts-v1",
            List.of(new GeneratedSuggestion("Atomicity","Nguyên tử",List.of(new Evidence(101L,"Nguyên tử"))))),5);
    }
    @Test void scopedWindowAndDraftPreserveUnicodeAndDoNotCreateConcepts() {
        var window=repository.readWindow(1L,1L,1L,0,1);
        assertEquals(List.of(101L),window.contexts().stream().map(Context::chunkId).toList());
        assertEquals(1,window.nextChunkIndex());
        var batch=repository.saveDraft(1L,1L,1L,window,extraction(window));
        assertTrue(batch.sourceCurrent()); assertEquals("PENDING",batch.suggestions().getFirst().state());
        assertEquals("Nguyên tử",batch.suggestions().getFirst().citations().getFirst().quote());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM concepts",Integer.class));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->repository.findOwnedBatch(1L,1L,2L,batch.id())).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->repository.readWindow(2L,1L,1L,0,1)).getStatusCode().value());
        assertEquals(422,assertThrows(ResponseStatusException.class,()->repository.readWindow(1L,1L,1L,Integer.MAX_VALUE,20)).getStatusCode().value());
    }
    @Test void changedPublicationRejectsFinalizeAndMarksExistingDraftStale() {
        var window=repository.readWindow(1L,1L,1L,0,1);
        var batch=repository.saveDraft(1L,1L,1L,window,extraction(window));
        jdbc.update("INSERT INTO document_ingestions(id,document_id,requested_by,status) VALUES ('version-2',1,1,'INDEXED')");
        jdbc.update("UPDATE documents SET published_ingestion_id='version-2' WHERE id=1");
        assertFalse(repository.findOwnedBatch(1L,1L,1L,batch.id()).sourceCurrent());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->repository.saveDraft(1L,1L,1L,window,extraction(window))).getStatusCode().value());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM concept_suggestion_batches",Integer.class));
        jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=1");
        jdbc.update("DELETE FROM documents WHERE id=1");
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM concept_suggestions",Integer.class));
    }
    @Test void rebuildingKeepsOldPublicationButNoPublishedVersionIsConflict() {
        jdbc.update("UPDATE documents SET ingestion_status='FAILED' WHERE id=1");
        assertEquals(2,repository.readWindow(1L,1L,1L,0,20).contexts().size());
        jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=1");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->repository.readWindow(1L,1L,1L,0,20)).getStatusCode().value());
    }
}
