package com.studyhub.ai.concept;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.studyhub.ai.concept.ConceptSuggestionModels.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ConceptSuggestionConcurrencyIT extends ConceptPostgresSupport {
    static final String SCHEMA="concept_concurrent_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) throws Exception { database(registry,SCHEMA); }
    @AfterAll static void drop() throws Exception { cleanup(SCHEMA); }
    @MockitoBean ConceptExtractionClient client;
    @Autowired ConceptSuggestionRepository repository;
    ExtractionResult result(SourceWindow window) {
        return new ExtractionResult("gpt-4.1-mini",PROMPT_VERSION,List.of(new GeneratedSuggestion("Atomicity","Nguyên tử",
            List.of(new Evidence(window.contexts().getFirst().chunkId(),"Atomicity")))));
    }
    @BeforeEach void generation() {
        doAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());return result(call.getArgument(0));
        }).when(client).extract(any(),anyInt());
    }
    @Test void simultaneousConfirmRetriesCreateOneConceptOnPostgres() throws Exception {
        var f=fixture();String endpoint=confirm(f,generate(f));var body=Map.of("name","Atomicity","description","Reviewed");
        {
            assertEquals(Set.of(201,200),new HashSet<>(simultaneous(
                ()->send("POST",endpoint,f.token(),body).statusCode(),()->send("POST",endpoint,f.token(),body).statusCode())));
            assertEquals(1,concepts(f));
        }
    }
    @Test void uniqueNameCollisionRollsBackOneOfTwoSuggestions() throws Exception {
        var f=fixture();var one=generate(f);var two=generate(f);var body=Map.of("name","Atomicity","description","Reviewed");
        {
            assertEquals(Set.of(201,409),new HashSet<>(simultaneous(
                ()->send("POST",confirm(f,one),f.token(),body).statusCode(),()->send("POST",confirm(f,two),f.token(),body).statusCode())));
            assertEquals(1,concepts(f));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM concept_suggestions s JOIN concept_suggestion_batches b ON b.id=s.batch_id WHERE b.document_id=? AND s.state='PENDING'",Integer.class,f.document()));
        }
    }
    @Test void concurrentDifferentReviewsCannotOverwriteFirstConfirmation() throws Exception {
        var f=fixture();String endpoint=confirm(f,generate(f));
        {
            assertEquals(Set.of(201,409),new HashSet<>(simultaneous(
                ()->send("POST",endpoint,f.token(),Map.of("name","First","description","First review")).statusCode(),
                ()->send("POST",endpoint,f.token(),Map.of("name","Second","description","Second review")).statusCode())));
            assertEquals(1,concepts(f));
        }
    }
    List<Integer> simultaneous(Callable<Integer> first,Callable<Integer> second) throws Exception {
        var gate=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{gate.await(10,TimeUnit.SECONDS);return first.call();});
            var b=pool.submit(()->{gate.await(10,TimeUnit.SECONDS);return second.call();});
            return List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }
    }
    @Test void publishedWindowIsScopedOrderedAndReadableDuringFailedRebuild() throws Exception {
        var f=fixture();var other=fixture();
        jdbc.update("""
            INSERT INTO document_chunks(document_id,ingestion_id,chunk_index,content,page_start,page_end,
                embedding,embedding_model,embedding_dimensions,embedding_text,metadata)
            VALUES (?,?,1,'Isolation',2,2,'[1,0,0]'::vector,'fixture',3,'Isolation','{}'::jsonb)
            """,f.document(),f.version());
        var first=repository.readWindow(f.course(),f.document(),f.owner(),0,1);
        assertEquals(f.chunk(),first.contexts().getFirst().chunkId());assertEquals(1,first.nextChunkIndex());
        var second=repository.readWindow(f.course(),f.document(),f.owner(),1,1);
        assertEquals("Isolation",second.contexts().getFirst().content());assertNull(second.nextChunkIndex());
        jdbc.update("UPDATE documents SET ingestion_status='FAILED' WHERE id=?",f.document());
        assertEquals(2,repository.readWindow(f.course(),f.document(),f.owner(),0,20).contexts().size());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->repository.readWindow(other.course(),f.document(),f.owner(),0,20));
    }
    @Test void publishBeforeConfirmAcquiresSourceLockRejectsApproval() throws Exception { confirmSourceRace(false); }
    @Test void deleteBeforeConfirmAcquiresSourceLockDoesNotLeaveConcept() throws Exception { confirmSourceRace(true); }
    void confirmSourceRace(boolean delete) throws Exception {
        var f=fixture();String endpoint=confirm(f,generate(f));
        try(var connection=jdbc.getDataSource().getConnection();var pool=Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try(var lock=connection.prepareStatement("SELECT id FROM documents WHERE id=? FOR UPDATE")) {
                lock.setLong(1,f.document());try(var ignored=lock.executeQuery()) { assertTrue(ignored.next()); }
            }
            var pending=pool.submit(()->send("POST",endpoint,f.token(),Map.of("name","Atomicity","description","Reviewed")));
            // Publication wins the Document row lock; confirmation must recheck after acquiring it.
            try(var sql=connection.createStatement()) {
                sql.executeUpdate("UPDATE documents SET published_ingestion_id=NULL WHERE id="+f.document());
                if(delete) sql.executeUpdate("DELETE FROM documents WHERE id="+f.document());
            }
            connection.commit();
            assertEquals(delete?404:409,pending.get(15,TimeUnit.SECONDS).statusCode());assertEquals(0,concepts(f));
        }
    }
    @Test void publicationWhileGenerationWaitsCannotSaveAStaleDraft() throws Exception { race(false); }
    @Test void documentDeleteWhileGenerationWaitsDoesNotLeaveDraft() throws Exception { race(true); }
    void race(boolean delete) throws Exception {
        var f=fixture();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());entered.countDown();
            assertTrue(release.await(15,TimeUnit.SECONDS));return result(call.getArgument(0));
        }).when(client).extract(any(),anyInt());
        try(var pool=Executors.newSingleThreadExecutor()) {
            var pending=pool.submit(()->send("POST",path(f),f.token(),Map.of()));
            try {
                assertTrue(entered.await(15,TimeUnit.SECONDS));
                if(delete) call("DELETE","/api/documents/"+f.document(),f.token(),null,204);
                else {
                    String newer=UUID.randomUUID().toString();
                    jdbc.update("INSERT INTO document_ingestions(id,document_id,requested_by,status) VALUES (?,?,?,'INDEXED')",newer,f.document(),f.owner());
                    jdbc.update("UPDATE documents SET published_ingestion_id=? WHERE id=?",newer,f.document());
                }
            } finally { release.countDown(); }
            assertEquals(delete?404:409,pending.get(15,TimeUnit.SECONDS).statusCode());
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM concept_suggestion_batches WHERE document_id=?",Integer.class,f.document()));
            assertEquals(0,concepts(f));
        }
    }
}
