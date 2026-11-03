package com.studyhub.ai.quiz;

import static com.studyhub.ai.quiz.QuizGenerationModels.*;
import static com.studyhub.ai.quiz.QuizGenerationSourceRepository.*;
import com.studyhub.question.QuestionType;
import com.studyhub.question.dto.*;
import com.studyhub.quiz.*;
import com.studyhub.quiz.dto.QuizResponse;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Repository
public class QuizGenerationRepository {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final ObjectMapper json;private final QuizGenerationSourceRepository sources;
    public QuizGenerationRepository(JdbcTemplate jdbc,PlatformTransactionManager manager,ObjectMapper json,QuizGenerationSourceRepository sources) {
        this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.json=json;this.sources=sources;
    }
    public Optional<GenerationResponse> findCompleted(Long user,String key,String fingerprint) {
        var rows=jdbc.query("SELECT fingerprint,quiz_id,response_json FROM quiz_generations WHERE user_id=? AND request_key=?",(rs,n)->new Object[]{rs.getString(1),rs.getObject(2,Long.class),rs.getString(3)},user,key);
        if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();
        if(!fingerprint.equals(row[0]))throw conflict("Idempotency key was used with a different request.");
        if(row[1]==null)throw conflict("Generated Quiz was deleted. Use a new key.");
        requireQuiz((Long)row[1],user);
        return Optional.of(json.readValue((String)row[2],GenerationResponse.class));
    }
    public GenerationMetadata getGeneration(Long quiz,Long user) {
        requireQuiz(quiz,user);
        var snapshots=jdbc.queryForList("SELECT response_json FROM quiz_generations WHERE quiz_id=? AND user_id=?",String.class,quiz,user);
        if(snapshots.isEmpty())throw missing();
        var g=json.readValue(snapshots.getFirst(),GenerationResponse.class).generation();
        return new GenerationMetadata(g.id(),g.model(),g.promptVersion(),g.coverage(),g.evidence(),sources.current(g.coverage()));
    }
    /** Internal snapshot lookup; callers must establish Quiz ownership first. */
    public Optional<GenerationResponse> findSnapshot(Long quiz) {
        return jdbc.queryForList("SELECT response_json FROM quiz_generations WHERE quiz_id=?", String.class, quiz)
                .stream().findFirst().map(value -> json.readValue(value, GenerationResponse.class));
    }
    private void requireQuiz(Long quiz,Long user) {
        if(jdbc.queryForList("SELECT q.id FROM quizzes q JOIN courses c ON c.id=q.course_id JOIN workspaces w ON w.id=c.workspace_id WHERE q.id=? AND w.owner_id=?",Long.class,quiz,user).isEmpty())throw missing();
    }
    public GenerationResponse saveDraft(SourceWindow window,ValidatedResult result,Long user,String key,String fingerprint,String title) {
        return tx.execute(status->{
            sources.lockAndVerify(window,user);
            long quiz=insert("INSERT INTO quizzes(course_id,title,status,source_type) VALUES (?,?,'DRAFT','AI_GENERATED')",window.courseId(),title);
            var questions=new ArrayList<QuestionResponse>();var evidence=new ArrayList<QuestionEvidence>();
            for(var q:result.questions()) {
                long question=insert("INSERT INTO questions(quiz_id,question_text,question_type) VALUES (?,?,'MULTIPLE_CHOICE')",quiz,q.text());
                var options=new ArrayList<AnswerOptionResponse>();
                for(var o:q.options()) options.add(new AnswerOptionResponse(insert("INSERT INTO answer_options(question_id,option_text,is_correct) VALUES (?,?,?)",question,o.text(),o.correct()),o.text(),o.correct()));
                questions.add(new QuestionResponse(question,quiz,q.text(),QuestionType.MULTIPLE_CHOICE,null,List.copyOf(options),null));
                evidence.add(new QuestionEvidence(question,q.citations()));
            }
            String id=UUID.randomUUID().toString();
            var response=new GenerationResponse(new QuizResponse(quiz,window.courseId(),title,QuizStatus.DRAFT,QuizSourceType.AI_GENERATED),List.copyOf(questions),
                new GenerationMetadata(id,result.model(),result.promptVersion(),window.coverage(),List.copyOf(evidence),true));
            jdbc.update("INSERT INTO quiz_generations(id,user_id,request_key,fingerprint,quiz_id,model,prompt_version,coverage_json,response_json) VALUES (?,?,?,?,?,?,?,?,?)",
                id,user,key,fingerprint,quiz,result.model(),result.promptVersion(),json.writeValueAsString(window.coverage()),json.writeValueAsString(response));
            for(var q:evidence) for(var citation:q.citations()) {
                String version=window.coverage().stream().filter(c->c.documentId().equals(citation.documentId())).findFirst().orElseThrow().ingestionId();
                jdbc.update("INSERT INTO quiz_question_evidence(generation_id,question_id,document_id,ingestion_id,chunk_id,citation_json) VALUES (?,?,?,?,?,?)",id,q.questionId(),citation.documentId(),version,citation.chunkId(),json.writeValueAsString(citation));
            }
            return response;
        });
    }
    private long insert(String sql,Object... args) {
        var key=new GeneratedKeyHolder();
        jdbc.update(connection->{var statement=connection.prepareStatement(sql,new String[]{"id"});for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
