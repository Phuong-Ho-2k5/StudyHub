package com.studyhub.ai.job;

import static com.studyhub.ai.job.AIJobModels.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AIJobRepository {
    private final JdbcTemplate jdbc;
    public AIJobRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    Optional<Snapshot> find(UUID id) {return query("SELECT * FROM ai_jobs WHERE id=?",id.toString());}
    Optional<Snapshot> lock(UUID id) {return query("SELECT * FROM ai_jobs WHERE id=? FOR UPDATE",id.toString());}
    Optional<Snapshot> findOwned(UUID id,long user) {
        return query("""
            SELECT j.* FROM ai_jobs j JOIN courses c ON c.id=j.course_id
            JOIN workspaces w ON w.id=c.workspace_id WHERE j.id=? AND j.requested_by=? AND w.owner_id=?
            """,id.toString(),user,user);
    }
    void insert(UUID id,CreateAIJob command,long user) {
        jdbc.update("""
            INSERT INTO ai_jobs(id,requested_by,course_id,document_id,type,status,created_at,updated_at)
            VALUES (?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,id.toString(),user,command.courseId(),command.documentId(),command.type().name());
    }
    boolean claim(UUID id,UUID token) {
        return jdbc.update("""
            UPDATE ai_jobs SET status='PROCESSING',claim_token=?,
            started_at=GREATEST(CURRENT_TIMESTAMP,created_at),updated_at=GREATEST(CURRENT_TIMESTAMP,updated_at)
            WHERE id=? AND status='PENDING'
            """,token.toString(),id.toString())==1;
    }
    boolean finish(AIJobClaim claim,AIJobResult result,AIJobErrorCode code) {
        return jdbc.update("""
            UPDATE ai_jobs SET status=?,claim_token=NULL,result_type=?,result_id=?,error_code=?,
            finished_at=GREATEST(CURRENT_TIMESTAMP,started_at),updated_at=GREATEST(CURRENT_TIMESTAMP,updated_at)
            WHERE id=? AND status='PROCESSING' AND claim_token=?
            """,result==null?"FAILED":"COMPLETED",result==null?null:result.type().name(),result==null?null:result.id(),
            code==null?null:code.name(),claim.jobId().toString(),claim.token().toString())==1;
    }
    private Optional<Snapshot> query(String sql,Object... args) {return jdbc.query(sql,this::map,args).stream().findFirst();}
    private Instant instant(ResultSet rs,String column) throws SQLException {
        var value=rs.getTimestamp(column);return value==null?null:value.toInstant();
    }
    private Snapshot map(ResultSet rs,int row) throws SQLException {
        String result=rs.getString("result_type"),error=rs.getString("error_code");
        return new Snapshot(UUID.fromString(rs.getString("id")),rs.getLong("requested_by"),rs.getLong("course_id"),
            rs.getObject("document_id",Long.class),AIJobType.valueOf(rs.getString("type")),AIJobStatus.valueOf(rs.getString("status")),
            rs.getString("claim_token"),result==null?null:new AIJobResult(AIJobResultType.valueOf(result),rs.getString("result_id")),
            error==null?null:AIJobErrorCode.valueOf(error),instant(rs,"created_at"),instant(rs,"updated_at"),instant(rs,"started_at"),instant(rs,"finished_at"));
    }
}
