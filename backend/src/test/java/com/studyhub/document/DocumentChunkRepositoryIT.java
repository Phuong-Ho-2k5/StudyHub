package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyhub.document.dto.DocumentExtractionResponse.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.web.server.ResponseStatusException;

/** Optional real PostgreSQL/pgvector tests: mvnw -Ppgvector-it verify. Uses its own disposable schema. */
@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL", matches=".+")
class DocumentChunkRepositoryIT {
    static JdbcTemplate jdbc;
    static DocumentChunkRepository repository;
    static String schema;
    static String applicationName;
    long userId;
    long documentId;
    DocumentService.ExtractionSource source;

    @BeforeAll static void migrate() {
        schema = "ingestion_" + UUID.randomUUID().toString().replace("-", "");
        applicationName = "ingestion_lock_test_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("PGVECTOR_TEST_URL");
        String user = System.getenv("PGVECTOR_TEST_USER");
        String password = System.getenv("PGVECTOR_TEST_PASSWORD");
        Flyway.configure().dataSource(url,user,password).defaultSchema(schema)
                .locations("classpath:db/migration","classpath:db/vendor/postgresql").load().migrate();
        var ds = new DriverManagerDataSource(url,user,password);
        var props = new Properties(); props.setProperty("currentSchema",schema + ",public");
        props.setProperty("ApplicationName",applicationName); ds.setConnectionProperties(props);
        jdbc = new JdbcTemplate(ds);
        repository = new DocumentChunkRepository(jdbc,new DataSourceTransactionManager(ds));
    }
    @AfterAll static void drop() { if (jdbc != null) jdbc.execute("DROP SCHEMA " + schema + " CASCADE"); }
    @BeforeEach void seed() {
        userId = jdbc.queryForObject("INSERT INTO users(email,password_hash,name) VALUES (?, 'test', 'Owner') RETURNING id",Long.class,UUID.randomUUID()+"@example.com");
        long workspace = jdbc.queryForObject("INSERT INTO workspaces(name,owner_id) VALUES ('Workspace',?) RETURNING id",Long.class,userId);
        long course = jdbc.queryForObject("INSERT INTO courses(name,workspace_id) VALUES ('Course',?) RETURNING id",Long.class,workspace);
        documentId = jdbc.queryForObject("INSERT INTO documents(course_id,title,file_name,file_type,storage_path,managed_file) VALUES (?, 'PDF','lesson.pdf','application/pdf','key.pdf',true) RETURNING id",Long.class,course);
        source = new DocumentService.ExtractionSource(documentId,"lesson.pdf","key.pdf",new byte[]{1});
    }
    @AfterEach void clean() { jdbc.update("DELETE FROM users WHERE id=?",userId); }
    Chunk chunk(int index, String content) {
        return new Chunk(documentId,"lesson.pdf",index,content,"Heading\n"+content,List.of("Heading"),1,1,
                List.of("block"),List.of(new SourceReference("block",1,new BoundingBox(1,2,3,4,"TOPLEFT"))),
                "overlap",List.of(new SourceReference("block",1,new BoundingBox(1,2,3,4,"TOPLEFT"))),5,"tokenizer@rev");
    }
    void persist(List<Chunk> chunks) {
        repository.persist(source,userId,chunks,chunks.stream().map(c -> List.of(1.0,0.0,0.0)).toList(),"model@rev",3);
    }
    long count() { return jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id=?",Long.class,documentId); }

    @Test void roundtripsEmbeddedTextAndCompleteProvenanceWithoutChangingStatus() throws Exception {
        persist(List.of(chunk(0,"body")));
        var row = jdbc.queryForMap("SELECT embedding_text,metadata::text,embedding_model,embedding_dimensions FROM document_chunks WHERE document_id=?",documentId);
        assertEquals("Heading\nbody",row.get("embedding_text"));
        var metadata = new ObjectMapper().readTree((String)row.get("metadata"));
        assertEquals("Heading",metadata.path("headings").get(0).asText());
        assertEquals(3,metadata.path("sourceRefs").get(0).path("bbox").path("r").asDouble());
        assertEquals("overlap",metadata.path("overlapContent").asText());
        assertEquals(5,metadata.path("tokenCount").asInt());
        assertEquals("tokenizer@rev",metadata.path("tokenizerModel").asText());
        assertEquals("READY",jdbc.queryForObject("SELECT status FROM documents WHERE id=?",String.class,documentId));
    }
    @Test void laterInsertFailureRollsBackAllChunks() {
        assertThrows(RuntimeException.class,() -> persist(List.of(chunk(0,"first"),chunk(1," "))));
        assertEquals(0,count());
    }
    @Test void repeatedIngestionReturnsConflict() {
        persist(List.of(chunk(0,"first")));
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> persist(List.of(chunk(0,"again")))).getStatusCode().value());
        assertEquals(1,count());
    }
    @Test void rechecksDeletedDocumentAndOwner() {
        assertEquals(404,assertThrows(ResponseStatusException.class,() -> repository.persist(source,-1L,List.of(chunk(0,"body")),List.of(List.of(1.0,0.0,0.0)),"model@rev",3)).getStatusCode().value());
        jdbc.update("DELETE FROM documents WHERE id=?",documentId);
        assertEquals(404,assertThrows(ResponseStatusException.class,() -> persist(List.of(chunk(0,"body")))).getStatusCode().value());
        assertEquals(0,count());
    }
    @Test void changedSourceCannotReceiveStaleChunks() {
        jdbc.update("UPDATE documents SET storage_path='replacement.pdf' WHERE id=?",documentId);
        assertEquals(409,assertThrows(ResponseStatusException.class,() -> persist(List.of(chunk(0,"body")))).getStatusCode().value());
        assertEquals(0,count());
    }
    @Test void concurrentIngestionStoresOneFullSet() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> work = () -> { start.await(); try { persist(List.of(chunk(0,"body"))); return 200; }
                catch (ResponseStatusException error) { return error.getStatusCode().value(); } };
            var first=pool.submit(work); var second=pool.submit(work); start.countDown();
            var statuses = new ArrayList<>(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)));
            Collections.sort(statuses); assertEquals(List.of(200,409),statuses); assertEquals(1,count());
        }
    }

    @ParameterizedTest @ValueSource(strings={"courses","workspaces"})
    void ancestorDeletionWhileIngestionWaitsReturns404WithoutDeadlock(String parentTable) throws Exception {
        long parentId = jdbc.queryForObject(parentTable.equals("courses")
                ? "SELECT course_id FROM documents WHERE id=?"
                : "SELECT c.workspace_id FROM documents d JOIN courses c ON c.id=d.course_id WHERE d.id=?",
                Long.class,documentId);
        // Lock the ancestor first, as a cascading DELETE does. Ingestion must wait before locking descendants.
        try (var pool=Executors.newSingleThreadExecutor(); var deletion=jdbc.getDataSource().getConnection()) {
            deletion.setAutoCommit(false);
            try (var lock=deletion.prepareStatement("SELECT id FROM "+parentTable+" WHERE id=? FOR UPDATE")) {
                lock.setLong(1,parentId);
                try (var result=lock.executeQuery()) { assertTrue(result.next()); }
            }
            var ingestion=pool.submit(() -> {
                try { persist(List.of(chunk(0,"body"))); return 200; }
                catch (ResponseStatusException error) { return error.getStatusCode().value(); }
            });
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            boolean waiting=false;
            while (System.nanoTime()<deadline) {
                waiting=jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock'",
                        Long.class,applicationName)>0;
                if (waiting) break;
                Thread.sleep(20);
            }
            assertTrue(waiting,"Ingestion did not reach the ancestor lock");
            try (var delete=deletion.prepareStatement("DELETE FROM "+parentTable+" WHERE id=?")) {
                delete.setLong(1,parentId); delete.executeUpdate();
            }
            deletion.commit();
            assertEquals(404,ingestion.get(10,TimeUnit.SECONDS));
            assertEquals(0,count());
        }
    }
}
