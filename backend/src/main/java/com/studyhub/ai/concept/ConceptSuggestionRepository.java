package com.studyhub.ai.concept;

import static com.studyhub.ai.concept.ConceptSuggestionModels.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.studyhub.ai.dto.CourseCitationResponse;
import com.studyhub.concept.ConceptService;
import com.studyhub.concept.ConceptStatus;
import com.studyhub.concept.dto.CreateConceptRequest;
import java.sql.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class ConceptSuggestionRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final ConceptService concepts;
    public ConceptSuggestionRepository(JdbcTemplate jdbc,PlatformTransactionManager manager,ObjectMapper json,ConceptService concepts) {
        this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.json=json;this.concepts=concepts;
    }

    public SourceWindow readWindow(Long courseId,Long documentId,Long userId,int start,int limit) {
        if(start<0 || limit<1 || limit>20) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid chunk window.");
        // One SQL snapshot covers authorization, publication, and every included chunk.
        var rows=jdbc.query("""
            SELECT d.published_ingestion_id,d.title,d.managed_file,i.status,ch.id,ch.chunk_index,
                ch.content,ch.page_start,ch.page_end,
                EXISTS(SELECT 1 FROM document_chunks all_ch WHERE all_ch.document_id=d.id
                    AND all_ch.ingestion_id=d.published_ingestion_id) AS has_chunks
            FROM documents d JOIN courses c ON c.id=d.course_id JOIN workspaces w ON w.id=c.workspace_id
            LEFT JOIN document_ingestions i ON i.id=d.published_ingestion_id AND i.document_id=d.id
            LEFT JOIN document_chunks ch ON ch.document_id=d.id AND ch.ingestion_id=i.id AND ch.chunk_index>=?
            WHERE c.id=? AND d.id=? AND w.owner_id=? ORDER BY ch.chunk_index ASC LIMIT ?
            """,(rs,n)->new WindowRow(rs.getString(1),rs.getString(4),rs.getBoolean("has_chunks"),
                rs.getObject("id")==null?null:new Context(rs.getLong("id"),documentId,rs.getString("title"),
                    rs.getInt("chunk_index"),rs.getString("content"),rs.getObject("page_start",Integer.class),
                    rs.getObject("page_end",Integer.class),rs.getBoolean("managed_file"))),start,courseId,documentId,userId,limit+1);
        if(rows.isEmpty()) throw missing();
        var first=rows.getFirst();
        if(first.version()==null || !"INDEXED".equals(first.status()) || !first.hasChunks())
            throw conflict("Document has no published content. Ingest it first.");
        if(first.context()==null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Chunk window is outside the Document.");
        Integer next=rows.size()>limit?rows.get(limit).context().chunkIndex():null;
        return new SourceWindow(courseId,documentId,first.version(),start,
            rows.stream().limit(limit).map(WindowRow::context).toList(),next);
    }
    private record WindowRow(String version,String status,boolean hasChunks,Context context) {}

    public DraftBatch saveDraft(Long courseId,Long documentId,Long userId,SourceWindow window,ValidatedExtraction result) {
        return tx.execute(status->{
            String current=lockDocument(courseId,documentId,userId);
            if(!Objects.equals(current,window.publishedIngestionId()) || !courseId.equals(window.courseId())
                || !documentId.equals(window.documentId())) throw conflict("Document source changed. Generate suggestions again.");
            var fresh=readWindow(courseId,documentId,userId,window.requestedStartChunkIndex(),window.contexts().size());
            if(!fresh.contexts().equals(window.contexts())) throw conflict("Document source changed. Generate suggestions again.");
            String id=UUID.randomUUID().toString();
            var coverage=new Coverage(window.requestedStartChunkIndex(),window.contexts().stream().map(Context::chunkId).toList(),window.nextChunkIndex());
            jdbc.update("""
                INSERT INTO concept_suggestion_batches(id,user_id,course_id,document_id,published_ingestion_id,model,prompt_version,coverage_json)
                VALUES (?,?,?,?,?,?,?,?)
                """,id,userId,courseId,documentId,current,result.model(),result.promptVersion(),write(coverage));
            for(int ordinal=0;ordinal<result.suggestions().size();ordinal++) {
                var item=result.suggestions().get(ordinal);
                jdbc.update("INSERT INTO concept_suggestions(batch_id,ordinal,name,description,citations_json) VALUES (?,?,?,?,?)",
                    id,ordinal,item.name(),item.description(),write(item.citations()));
            }
            return findOwnedBatch(courseId,documentId,userId,id);
        });
    }

    public DraftBatch findOwnedBatch(Long courseId,Long documentId,Long userId,String batchId) {
        var batches=jdbc.query("""
            SELECT b.*,d.published_ingestion_id AS current_version
            FROM concept_suggestion_batches b JOIN documents d ON d.id=b.document_id AND d.course_id=b.course_id
            JOIN courses c ON c.id=b.course_id JOIN workspaces w ON w.id=c.workspace_id
            WHERE b.id=? AND b.course_id=? AND b.document_id=? AND b.user_id=? AND w.owner_id=?
            """,(rs,n)->new DraftBatch(rs.getString("id"),courseId,documentId,rs.getString("published_ingestion_id"),
                rs.getString("model"),rs.getString("prompt_version"),rs.getTimestamp("created_at").toInstant(),
                Objects.equals(rs.getString("published_ingestion_id"),rs.getString("current_version")),
                read(rs.getString("coverage_json"),new TypeReference<Coverage>() {}),List.of()),batchId,courseId,documentId,userId,userId);
        if(batches.isEmpty()) throw missing();
        var batch=batches.getFirst();
        var items=jdbc.query("SELECT * FROM concept_suggestions WHERE batch_id=? ORDER BY ordinal",(rs,n)->item(rs),batchId);
        return new DraftBatch(batch.id(),courseId,documentId,batch.publishedIngestionId(),batch.model(),batch.promptVersion(),
            batch.createdAt(),batch.sourceCurrent(),batch.coverage(),List.copyOf(items));
    }
    private DraftItem item(ResultSet rs) throws SQLException {
        return new DraftItem(rs.getLong("id"),rs.getString("name"),rs.getString("description"),rs.getString("state"),
            rs.getObject("concept_id",Long.class),read(rs.getString("citations_json"),new TypeReference<List<CourseCitationResponse>>() {}));
    }

    public ConfirmationResult confirm(Long courseId,Long documentId,Long userId,String batchId,Long itemId,String name,String description) {
        return tx.execute(status->{
            String current=lockDocument(courseId,documentId,userId);
            var owned=jdbc.queryForList("SELECT id FROM concept_suggestion_batches WHERE id=? AND course_id=? AND document_id=? AND user_id=? FOR UPDATE",
                String.class,batchId,courseId,documentId,userId);
            if(owned.isEmpty()) throw missing();
            var batch=findOwnedBatch(courseId,documentId,userId,batchId);
            var items=jdbc.query("SELECT * FROM concept_suggestions WHERE id=? AND batch_id=? FOR UPDATE",(rs,n)->
                new LockedItem(item(rs),rs.getString("confirmed_name"),rs.getString("confirmed_description")),itemId,batchId);
            if(items.isEmpty()) throw missing();
            var locked=items.getFirst();var item=locked.item();
            if("CONFIRMED".equals(item.state())) {
                if(item.conceptId()==null || !name.equals(locked.name()) || !description.equals(locked.description()))
                    throw conflict("Suggestion was already confirmed. Edit the Concept separately.");
                return new ConfirmationResult(false,new ConfirmationResponse(itemId,concepts.getConcept(item.conceptId(),userId)));
            }
            if(!Objects.equals(current,batch.publishedIngestionId())) throw conflict("Document source changed. Generate suggestions again.");
            for(var citation:item.citations()) {
                var contents=jdbc.queryForList("SELECT content FROM document_chunks WHERE id=? AND document_id=? AND ingestion_id=?",String.class,
                    citation.chunkId(),documentId,current);
                if(contents.size()!=1 || !contents.getFirst().contains(citation.quote())) throw conflict("Suggestion source is no longer available.");
            }
            var concept=concepts.createConcept(courseId,new CreateConceptRequest(name,description,0,ConceptStatus.NEW),userId);
            jdbc.update("""
                UPDATE concept_suggestions SET state='CONFIRMED',concept_id=?,confirmed_name=?,confirmed_description=?,confirmed_at=CURRENT_TIMESTAMP
                WHERE id=? AND batch_id=?
                """,concept.id(),name,description,itemId,batchId);
            return new ConfirmationResult(true,new ConfirmationResponse(itemId,concept));
        });
    }
    private record LockedItem(DraftItem item,String name,String description) {}

    private String lockDocument(Long courseId,Long documentId,Long userId) {
        var ancestors=jdbc.queryForList("SELECT workspace_id FROM courses WHERE id=?",Long.class,courseId);
        if(ancestors.isEmpty()) throw missing();
        Long workspace=ancestors.getFirst();
        if(jdbc.queryForList("SELECT id FROM workspaces WHERE id=? AND owner_id=? FOR UPDATE",Long.class,workspace,userId).isEmpty()
            || jdbc.queryForList("SELECT id FROM courses WHERE id=? AND workspace_id=? FOR UPDATE",Long.class,courseId,workspace).isEmpty()) throw missing();
        var documents=jdbc.query("SELECT published_ingestion_id FROM documents WHERE id=? AND course_id=? FOR UPDATE",
            (rs,n)->rs.getString(1),documentId,courseId);
        if(documents.isEmpty()) throw missing();
        return documents.getFirst();
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch(RuntimeException error) { throw new IllegalStateException("Cannot serialize Concept draft",error); }
    }
    private <T> T read(String value,TypeReference<T> type) {
        try { return json.readValue(value,type); }
        catch(RuntimeException error) { throw new IllegalStateException("Cannot read Concept draft",error); }
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Document or suggestion not found."); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
