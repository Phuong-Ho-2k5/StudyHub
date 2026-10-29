package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.studyhub.ai.*;
import com.studyhub.ai.dto.CourseChatRequest;
import com.studyhub.document.dto.DocumentExtractionResponse.Chunk;
import com.studyhub.search.CourseSearchService;
import com.studyhub.search.dto.CourseSearchResponse;
import com.studyhub.search.dto.CourseSearchResponse.SearchHitResponse;
import com.studyhub.security.ResourceAccessService;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.web.server.ResponseStatusException;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
class DocumentSourceRepositoryIT {
    static JdbcTemplate jdbc;static String schema;static DocumentChunkRepository chunks;
    static DocumentIngestionRepository queue;static DocumentSourceRepository sources;
    long owner,course,document;DocumentService.ExtractionSource file;
    @BeforeAll static void migrate() {
        schema="sources_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv("PGVECTOR_TEST_URL"),user=System.getenv("PGVECTOR_TEST_USER"),password=System.getenv("PGVECTOR_TEST_PASSWORD");
        Flyway.configure().dataSource(url,user,password).defaultSchema(schema)
            .locations("classpath:db/migration","classpath:db/vendor/postgresql").load().migrate();
        var ds=new DriverManagerDataSource(url,user,password);var properties=new Properties();
        properties.setProperty("currentSchema",schema+",public");ds.setConnectionProperties(properties);
        jdbc=new JdbcTemplate(ds);var tx=new DataSourceTransactionManager(ds);
        queue=new DocumentIngestionRepository(jdbc,tx,600,240,3);chunks=new DocumentChunkRepository(jdbc,tx,queue);
        sources=new DocumentSourceRepository(jdbc);
    }
    @AfterAll static void cleanup() { if(jdbc!=null)jdbc.execute("DROP SCHEMA "+schema+" CASCADE"); }
    @BeforeEach void seed() {
        owner=jdbc.queryForObject("INSERT INTO users(email,password_hash,name) VALUES (?,'test','Owner') RETURNING id",Long.class,UUID.randomUUID()+"@example.com");
        long workspace=jdbc.queryForObject("INSERT INTO workspaces(name,owner_id) VALUES ('Sources',?) RETURNING id",Long.class,owner);
        course=jdbc.queryForObject("INSERT INTO courses(name,workspace_id) VALUES ('DB',?) RETURNING id",Long.class,workspace);
        document=jdbc.queryForObject("INSERT INTO documents(course_id,title,file_name,file_type,storage_path,managed_file) VALUES (?,'DB','lesson.pdf','application/pdf','key.pdf',true) RETURNING id",Long.class,course);
        file=new DocumentService.ExtractionSource(document,"lesson.pdf","key.pdf",new byte[]{1});publish("Atomicity");
    }
    void publish(String content) {
        var chunk=new Chunk(document,"lesson.pdf",0,content,content,List.of(),1,1,List.of(),List.of(),"",List.of(),4,"model");
        chunks.persist(queue.claimNow(document,owner),file,List.of(chunk),List.of(List.of(1.,0.,0.)),"model",3);
        // DB permits unknown page provenance even though PDF extraction emits page numbers.
        jdbc.update("UPDATE document_chunks SET page_start=NULL,page_end=NULL WHERE document_id=?",document);
    }
    DocumentSourceRepository.PublishedSource current() {
        long id=jdbc.queryForObject("SELECT id FROM document_chunks WHERE document_id=?",Long.class,document);
        return sources.findPublished(course,document,id,owner).orElseThrow();
    }
    @Test void sourcesAreScopedAndOldIdsNeverResolveToReplacement() {
        var old=current();assertEquals("Atomicity",old.content());assertNull(old.pageStart());
        assertTrue(sources.findPublished(course,document,old.chunkId(),-1L).isEmpty());
        assertTrue(sources.findPublished(-1L,document,old.chunkId(),owner).isEmpty());
        assertTrue(sources.findPublished(course,-1L,old.chunkId(),owner).isEmpty());
        assertEquals(List.of(old),sources.findPublishedByIds(course,owner,List.of(old.chunkId())));
        publish("New version");assertTrue(sources.findPublished(course,document,old.chunkId(),owner).isEmpty());
        assertNotEquals(old.chunkId(),current().chunkId());assertEquals("New version",current().content());
    }
    @Test void pendingAndFailedRebuildKeepPreviousPublicationReadable() {
        var old=current();var claim=queue.claimNow(document,owner);
        assertEquals(old,sources.findPublished(course,document,old.chunkId(),owner).orElseThrow());
        queue.fail(claim,"INGESTION_FAILED");
        assertEquals(old,sources.findPublished(course,document,old.chunkId(),owner).orElseThrow());
    }
    @Test void publishWhileGenerationBlockedReturnsConflict() throws Exception { race(false); }
    @Test void deleteDocumentWhileGenerationBlockedReturnsConflict() throws Exception { race(true); }
    void race(boolean delete) throws Exception {
        var old=current();var hit=new SearchHitResponse(old.chunkId(),document,"DB",0,old.content(),null,null,.9);
        var search=mock(CourseSearchService.class);var access=mock(ResourceAccessService.class);var ai=mock(RagAnswerClient.class);
        when(search.search(anyLong(),anyLong(),any())).thenReturn(new CourseSearchResponse(course,"Question",List.of(hit)));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(ai.answer(anyString(),anyList())).thenAnswer(i->{entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));
            return new RagAnswerClient.RagAnswerResult("ANSWERED","Atomicity","model","course-rag-v2",List.of(new RagAnswerClient.Evidence(old.chunkId(),"Atomicity")));});
        var service=new CourseChatService(search,access,ai,new CitationMapper(sources));var request=new CourseChatRequest();request.setQuestion("Question");
        try(var pool=Executors.newSingleThreadExecutor()) {
            var pending=pool.submit(()->service.chat(course,owner,request));
            try { assertTrue(entered.await(10,TimeUnit.SECONDS));
                if(delete)jdbc.update("DELETE FROM documents WHERE id=?",document);else publish("Atomicity");
            } finally { release.countDown(); }
            var error=assertThrows(ExecutionException.class,()->pending.get(10,TimeUnit.SECONDS));
            assertEquals(409,((ResponseStatusException)error.getCause()).getStatusCode().value());
        }
    }
}
