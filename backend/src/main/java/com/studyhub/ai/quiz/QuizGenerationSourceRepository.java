package com.studyhub.ai.quiz;

import static com.studyhub.ai.quiz.QuizGenerationModels.*;
import com.studyhub.ai.concept.ConceptSuggestionModels.Context;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class QuizGenerationSourceRepository {
    private final JdbcTemplate jdbc;
    public QuizGenerationSourceRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Long documentCourse(Long document,Long user) {
        var ids=jdbc.queryForList("SELECT d.course_id FROM documents d JOIN courses c ON c.id=d.course_id JOIN workspaces w ON w.id=c.workspace_id WHERE d.id=? AND w.owner_id=?",Long.class,document,user);
        if(ids.isEmpty()) throw missing();return ids.getFirst();
    }
    public void requireScope(Long course,List<Long> documents,Long user) {
        String marks=String.join(",",Collections.nCopies(documents.size(),"?"));
        var args=new ArrayList<Object>();args.add(course);args.add(user);args.addAll(documents);
        var rows=jdbc.queryForList("SELECT d.id FROM documents d JOIN courses c ON c.id=d.course_id JOIN workspaces w ON w.id=c.workspace_id WHERE c.id=? AND w.owner_id=? AND d.id IN ("+marks+")",Long.class,args.toArray());
        if(!rows.containsAll(documents)) throw missing();
    }
    private record Row(Long document,String version,String status,boolean hasChunks,Context context) {}

    /** One statement gives a consistent, bounded source/ownership snapshot across selected Documents. */
    public SourceWindow read(Long course,List<Long> documents,Long user,int start,int limit) {
        if(documents.isEmpty() || documents.size()>5 || new HashSet<>(documents).size()!=documents.size()
            || start<0 || limit<documents.size() || limit>20) throw new IllegalArgumentException("Invalid source window");
        var sorted=documents.stream().sorted().toList();
        String marks=String.join(",",Collections.nCopies(sorted.size(),"?"));
        String sql="""
            WITH ranked AS (
                SELECT ch.*,ROW_NUMBER() OVER(PARTITION BY document_id ORDER BY chunk_index,id) AS rn
                FROM document_chunks ch
                WHERE ch.ingestion_id=(SELECT published_ingestion_id FROM documents WHERE id=ch.document_id)
                    AND chunk_index>=? AND document_id IN (%s)
            )
            SELECT d.id AS doc_id,d.title,d.managed_file,d.published_ingestion_id,i.status,
                ch.id AS chunk_id,ch.chunk_index,ch.content,ch.page_start,ch.page_end,
                EXISTS(SELECT 1 FROM document_chunks all_ch WHERE all_ch.document_id=d.id
                    AND all_ch.ingestion_id=d.published_ingestion_id) AS has_chunks
            FROM documents d JOIN courses c ON c.id=d.course_id JOIN workspaces w ON w.id=c.workspace_id
            LEFT JOIN document_ingestions i ON i.id=d.published_ingestion_id AND i.document_id=d.id
            LEFT JOIN ranked ch ON ch.document_id=d.id AND ch.ingestion_id=d.published_ingestion_id AND ch.rn<=?
            WHERE c.id=? AND w.owner_id=? AND d.id IN (%s) ORDER BY d.id,ch.chunk_index,ch.id
            """.formatted(marks,marks);
        var args=new ArrayList<Object>();args.add(start);args.addAll(sorted);args.add(limit);args.add(course);args.add(user);args.addAll(sorted);
        var rows=jdbc.query(sql,(rs,n)->new Row(rs.getLong("doc_id"),rs.getString("published_ingestion_id"),rs.getString("status"),rs.getBoolean("has_chunks"),
            rs.getObject("chunk_id")==null?null:new Context(rs.getLong("chunk_id"),rs.getLong("doc_id"),rs.getString("title"),rs.getInt("chunk_index"),rs.getString("content"),rs.getObject("page_start",Integer.class),rs.getObject("page_end",Integer.class),rs.getBoolean("managed_file"))),args.toArray());
        var byDoc=new LinkedHashMap<Long,List<Context>>();var versions=new HashMap<Long,String>();
        for(var row:rows) {byDoc.computeIfAbsent(row.document(),k->new ArrayList<>());versions.put(row.document(),row.version());}
        if(!byDoc.keySet().containsAll(sorted)) throw missing();
        for(var row:rows) {
            if(row.version()==null || !"INDEXED".equals(row.status()) || !row.hasChunks()) throw conflict("Document has no published content. Ingest it first.");
            if(row.context()!=null) byDoc.get(row.document()).add(row.context());
        }
        for(var contexts:byDoc.values()) if(contexts.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Source window is empty.");
        var selected=new ArrayList<Context>();
        for(int ordinal=0;selected.size()<limit;ordinal++) {
            int before=selected.size();
            for(var contexts:byDoc.values()) if(ordinal<contexts.size() && selected.size()<limit) selected.add(contexts.get(ordinal));
            if(before==selected.size()) break;
        }
        var coverage=sorted.stream().map(id->new DocumentCoverage(id,versions.get(id),selected.stream().filter(c->id.equals(c.documentId())).map(Context::chunkId).toList())).toList();
        return new SourceWindow(course,coverage,List.copyOf(selected));
    }
    /** Called only in the save transaction. Match ingestion's ancestor-first ordering. */
    public void lockAndVerify(SourceWindow window,Long user) {
        var parents=jdbc.queryForList("SELECT workspace_id FROM courses WHERE id=?",Long.class,window.courseId());
        if(parents.isEmpty()) throw missing();Long workspace=parents.getFirst();
        if(jdbc.queryForList("SELECT id FROM workspaces WHERE id=? AND owner_id=? FOR UPDATE",Long.class,workspace,user).isEmpty()
            || jdbc.queryForList("SELECT id FROM courses WHERE id=? AND workspace_id=? FOR UPDATE",Long.class,window.courseId(),workspace).isEmpty()) throw missing();
        for(var coverage:window.coverage().stream().sorted(Comparator.comparing(DocumentCoverage::documentId)).toList()) {
            var versions=jdbc.query("SELECT published_ingestion_id FROM documents WHERE id=? AND course_id=? FOR UPDATE",(rs,n)->rs.getString(1),coverage.documentId(),window.courseId());
            if(versions.isEmpty()) throw missing();
            if(!Objects.equals(versions.getFirst(),coverage.ingestionId())) throw conflict("Document source changed. Generate again.");
            for(var context:window.contexts()) if(context.documentId().equals(coverage.documentId())) {
                var contents=jdbc.queryForList("SELECT content FROM document_chunks WHERE id=? AND document_id=? AND ingestion_id=?",String.class,context.chunkId(),coverage.documentId(),coverage.ingestionId());
                if(contents.size()!=1 || !context.content().equals(contents.getFirst())) throw conflict("Document source changed. Generate again.");
            }
        }
    }
    public boolean current(List<DocumentCoverage> coverage) {
        return coverage.stream().allMatch(c->jdbc.queryForList("SELECT id FROM documents WHERE id=? AND published_ingestion_id=?",Long.class,c.documentId(),c.ingestionId()).size()==1);
    }
    static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Quiz or source not found."); }
    static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
