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
    static DocumentIngestionRepository queue;
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
        var manager=new DataSourceTransactionManager(ds);
        queue=new DocumentIngestionRepository(jdbc,manager,600,240,3);
        repository = new DocumentChunkRepository(jdbc,manager,queue);
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
        publish(queue.claimNow(documentId,userId),chunks);
    }
    void publish(DocumentIngestionRepository.Claim claim,List<Chunk> chunks) {
        repository.persist(claim,source,chunks,chunks.stream().map(c -> List.of(1.0,0.0,0.0)).toList(),"model@rev",3);
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
    @Test void repeatedIngestionPublishesNewVersionAndCleansOldVectors() {
        persist(List.of(chunk(0,"first")));
        String first=published();
        persist(List.of(chunk(0,"again")));
        assertNotEquals(first,published());
        assertEquals(1,count());
        assertEquals("again",jdbc.queryForObject("SELECT content FROM document_chunks WHERE document_id=?",String.class,documentId));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE ingestion_id=?",Integer.class,first));
    }
    @Test void rechecksDeletedDocumentAndOwner() {
        assertEquals(404,assertThrows(ResponseStatusException.class,() -> queue.claimNow(documentId,-1L)).getStatusCode().value());
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
        var claim=queue.claimNow(documentId,userId);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> work = () -> { start.await(); try { publish(claim,List.of(chunk(0,"body"))); return 200; }
                catch (ResponseStatusException error) { return error.getStatusCode().value(); } };
            var first=pool.submit(work); var second=pool.submit(work); start.countDown();
            var statuses = new ArrayList<>(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)));
            Collections.sort(statuses); assertEquals(List.of(200,409),statuses); assertEquals(1,count());
        }
    }

    String published(){return jdbc.queryForObject("SELECT published_ingestion_id FROM documents WHERE id=?",String.class,documentId);}

    @Test void semanticSearchRanksOnlyCompatiblePublishedChunksWithinOwnerCourse() {
        long course=jdbc.queryForObject("SELECT course_id FROM documents WHERE id=?",Long.class,documentId);
        var claim=queue.claimNow(documentId,userId);
        repository.persist(claim,source,List.of(chunk(0,"second"),chunk(1,"first"),chunk(2,"tie")),
                List.of(List.of(0.,1.,0.),List.of(1.,0.,0.),List.of(1.,0.,0.)),"model@rev",3);
        jdbc.update("INSERT INTO document_chunks(document_id,chunk_index,content,embedding,embedding_model,embedding_dimensions,ingestion_id) VALUES (?,3,'wrong dims','[1,0]'::vector,'model@rev',2,?)",documentId,claim.id());
        jdbc.update("INSERT INTO document_chunks(document_id,chunk_index,content,embedding,embedding_model,embedding_dimensions,ingestion_id) VALUES (?,4,'wrong model','[1,0,0]'::vector,'different@rev',3,?)",documentId,claim.id());
        var search=new com.studyhub.search.CourseSearchRepository(jdbc);
        var hits=search.search(course,userId,List.of(1.,0.,0.),"model@rev",3,2);
        assertEquals(List.of("first","tie"),hits.stream().map(com.studyhub.search.CourseSearchRepository.SearchHit::content).toList());
        assertEquals(1.,hits.getFirst().score(),1e-6);
        assertTrue(hits.getFirst().chunkId()<hits.getLast().chunkId());
        assertTrue(search.search(course,-1L,List.of(1.,0.,0.),"model@rev",3,5).isEmpty());
        assertTrue(search.search(-1L,userId,List.of(1.,0.,0.),"model@rev",3,5).isEmpty());
        assertEquals(3,search.search(course,userId,List.of(1.,0.,0.),"model@rev",3,20).size());
        var failed=queue.claimNow(documentId,userId);queue.fail(failed,"INGESTION_FAILED");
        assertEquals(3,search.search(course,userId,List.of(1.,0.,0.),"model@rev",3,20).size());
    }

    @Test void unpublishedVectorsNeverAppearInSemanticSearch() {
        jdbc.update("INSERT INTO document_chunks(document_id,chunk_index,content,embedding,embedding_model,embedding_dimensions) VALUES (?,0,'unpublished','[1,0,0]'::vector,'model@rev',3)",documentId);
        long course=jdbc.queryForObject("SELECT course_id FROM documents WHERE id=?",Long.class,documentId);
        assertTrue(new com.studyhub.search.CourseSearchRepository(jdbc).search(course,userId,List.of(1.,0.,0.),"model@rev",3,5).isEmpty());
    }

    @Test void semanticSearchDoesNotTakeGlobalTopKBeforeCourseFiltering() {
        persist(List.of(chunk(0,"owned")));
        long course=jdbc.queryForObject("SELECT course_id FROM documents WHERE id=?",Long.class,documentId);
        long workspace=jdbc.queryForObject("SELECT workspace_id FROM courses WHERE id=?",Long.class,course);
        long foreignCourse=jdbc.queryForObject("INSERT INTO courses(name,workspace_id) VALUES ('Other',?) RETURNING id",Long.class,workspace);
        long otherDoc=jdbc.queryForObject("INSERT INTO documents(course_id,title,file_name,file_type,storage_path,managed_file) VALUES (?,'Other','lesson.pdf','application/pdf','key.pdf',true) RETURNING id",Long.class,foreignCourse);
        var claim=queue.claimNow(otherDoc,userId);
        var other=new Chunk(otherDoc,"lesson.pdf",0,"other course","other",List.of(),1,1,List.of(),List.of(),"",List.of(),2,"model");
        repository.persist(claim,new DocumentService.ExtractionSource(otherDoc,"lesson.pdf","key.pdf",new byte[]{1}),
                List.of(other),List.of(List.of(0.,1.,0.)),"model@rev",3);
        var hits=new com.studyhub.search.CourseSearchRepository(jdbc).search(course,userId,List.of(0.,1.,0.),"model@rev",3,1);
        assertEquals(1,hits.size());assertEquals("owned",hits.getFirst().content());
    }

    @ParameterizedTest @ValueSource(strings={"publish","delete"})
    void searchWaitingForAiReadsCurrentPublication(String action) throws Exception {
        persist(List.of(chunk(0,"old")));
        long course=jdbc.queryForObject("SELECT course_id FROM documents WHERE id=?",Long.class,documentId);
        var ai=org.mockito.Mockito.mock(DocumentEmbeddingClient.class);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        org.mockito.Mockito.when(ai.embedQuery("Question")).thenAnswer(invocation->{
            entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");
            return new DocumentEmbeddingClient.EmbeddingBatch("model@rev",3,List.of(List.of(1.,0.,0.)));
        });
        var service=new com.studyhub.search.CourseSearchService(org.mockito.Mockito.mock(com.studyhub.security.ResourceAccessService.class),
                ai,new com.studyhub.search.CourseSearchRepository(jdbc));
        var request=new com.studyhub.search.dto.CourseSearchRequest();request.setQuery("Question");
        try(var pool=Executors.newSingleThreadExecutor()){
            var work=pool.submit(()->service.search(course,userId,request));
            try{
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                if(action.equals("publish"))persist(List.of(chunk(0,"new")));
                else jdbc.update("DELETE FROM documents WHERE id=?",documentId);
            } finally {release.countDown();}
            var result=work.get(10,TimeUnit.SECONDS);
            if(action.equals("publish"))assertEquals("new",result.items().getFirst().content());
            else assertTrue(result.items().isEmpty());
        }
    }

    @Test void concurrentBackgroundClaimsHaveOnlyOneWinner() throws Exception {
        new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()))
                .executeWithoutResult(status->queue.enqueueUploaded(documentId,userId));
        try(var pool=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);
            Callable<DocumentIngestionRepository.Claim> work=()->{start.await();return queue.claimNext();};
            var a=pool.submit(work);var b=pool.submit(work);start.countDown();
            int winners=(a.get(5,TimeUnit.SECONDS)==null?0:1)+(b.get(5,TimeUnit.SECONDS)==null?0:1);
            assertEquals(1,winners);
            assertEquals(1,jdbc.queryForObject("SELECT attempts FROM document_ingestions WHERE document_id=?",Integer.class,documentId));
        }
    }

    @Test void ownerChangeAfterClaimPreventsPublication() {
        var claim=queue.claimNow(documentId,userId);
        long other=jdbc.queryForObject("INSERT INTO users(email,password_hash,name) VALUES (?,'test','Other') RETURNING id",Long.class,UUID.randomUUID()+"@test.com");
        try {
            jdbc.update("UPDATE workspaces SET owner_id=? WHERE id=(SELECT c.workspace_id FROM documents d JOIN courses c ON c.id=d.course_id WHERE d.id=?)",other,documentId);
            assertEquals(404,assertThrows(ResponseStatusException.class,()->publish(claim,List.of(chunk(0,"stale")))).getStatusCode().value());
            assertEquals(0,count());
            assertNull(published());
        } finally {
            jdbc.update("UPDATE workspaces SET owner_id=? WHERE owner_id=?",userId,other);
            jdbc.update("DELETE FROM users WHERE id=?",other);
        }
    }

    @Test void deletedDocumentCannotBeRecreatedByAlreadyClaimedWorker() {
        var claim=queue.claimNow(documentId,userId);
        jdbc.update("DELETE FROM documents WHERE id=?",documentId);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->publish(claim,List.of(chunk(0,"stale")))).getStatusCode().value());
        assertEquals(0,count());
    }

    @Test void failedRebuildRollsBackDeletionAndKeepsPublishedVersion() {
        persist(List.of(chunk(0,"old")));
        String first=published();
        var rebuild=queue.claimNow(documentId,userId);
        assertThrows(RuntimeException.class,()->publish(rebuild,List.of(chunk(0,"new"),chunk(1," "))));
        queue.fail(rebuild,"INGESTION_FAILED");
        assertEquals(first,published());
        assertEquals("old",jdbc.queryForObject("SELECT content FROM document_chunks WHERE document_id=?",String.class,documentId));
        assertEquals("FAILED",jdbc.queryForObject("SELECT ingestion_status FROM documents WHERE id=?",String.class,documentId));
    }

    @Test void recoveredWorkerFencesOldPublication() {
        persist(List.of(chunk(0,"old")));
        var stale=queue.claimNow(documentId,userId);
        jdbc.update("UPDATE document_ingestions SET lease_until=TIMESTAMP '2000-01-01 00:00:00' WHERE id=?",stale.id());
        var current=queue.claimNext();
        assertEquals(409,assertThrows(ResponseStatusException.class,()->publish(stale,List.of(chunk(0,"stale")))).getStatusCode().value());
        assertEquals("old",jdbc.queryForObject("SELECT content FROM document_chunks WHERE document_id=?",String.class,documentId));
        publish(current,List.of(chunk(0,"current")));
        assertEquals("current",jdbc.queryForObject("SELECT content FROM document_chunks WHERE document_id=?",String.class,documentId));
    }

    @Test void readersSeeOldVersionUntilPublicationCommits() throws Exception {
        persist(List.of(chunk(0,"old")));
        String first=published();
        var claim=queue.claimNow(documentId,userId);
        var inserted=new CountDownLatch(1);var commit=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var work=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(
                    new DataSourceTransactionManager(jdbc.getDataSource())).executeWithoutResult(status->{
                publish(claim,List.of(chunk(0,"new")));inserted.countDown();
                try{if(!commit.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test commit timeout");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
            }));
            try {
                assertTrue(inserted.await(5,TimeUnit.SECONDS));
                assertEquals(first,published());
                assertEquals("old",jdbc.queryForObject("SELECT content FROM document_chunks WHERE document_id=?",String.class,documentId));
            } finally {commit.countDown();}
            work.get(10,TimeUnit.SECONDS);
            assertEquals(claim.id(),published());
            assertEquals("new",jdbc.queryForObject("SELECT content FROM document_chunks WHERE document_id=?",String.class,documentId));
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
