package com.studyhub.ai.job;
import static com.studyhub.ai.job.AIJobModels.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
@Repository
public class AIJobCommandRepository {
 public record Command(UUID jobId,long userId,long courseId,Long documentId,String mode,String requestJson,Map<Long,String> sources,String key,String fingerprint) {}
 private final JdbcTemplate jdbc;private final AIJobService jobs;private final TransactionTemplate tx;private final ObjectMapper json;
 public AIJobCommandRepository(JdbcTemplate jdbc,AIJobService jobs,PlatformTransactionManager manager,ObjectMapper json) {this.jdbc=jdbc;this.jobs=jobs;this.tx=new TransactionTemplate(manager);this.json=json;}
 public UUID enqueue(CreateAIJob create,Long user,String mode,String request,Map<Long,String> sources,String key,String fingerprint) {
  return tx.execute(status->{
   // Serialize reservations per requester before locking scope, including a missing key row.
   if(jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,user).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
   if(key!=null) {
    var existing=jdbc.query("SELECT job_id,fingerprint FROM ai_job_commands WHERE requested_by=? AND request_key=?",(rs,n)->new String[]{rs.getString(1),rs.getString(2)},user,key);
    if(!existing.isEmpty()) {
     if(!Objects.equals(fingerprint,existing.getFirst()[1]))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key used with a different request");
     UUID id=UUID.fromString(existing.getFirst()[0]);var job=jobs.get(id,user);
     if(job.status()==AIJobStatus.COMPLETED && job.result().type()==AIJobResultType.QUIZ && jdbc.queryForList("SELECT id FROM quizzes WHERE id=?",Long.class,Long.valueOf(job.result().id())).isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Generated Quiz was deleted. Use a new key.");
     return id;
    }
   }
   UUID id=jobs.enqueue(create,user);
   jdbc.update("INSERT INTO ai_job_commands(job_id,mode,request_json,sources_json,requested_by,request_key,fingerprint) VALUES (?,?,?,?,?,?,?)",id.toString(),mode,request,json.writeValueAsString(sources),user,key,fingerprint);
   return id;
  });
 }
 public Optional<UUID> replay(Long user,String key,String fingerprint) {
  var rows=jdbc.queryForList("SELECT job_id FROM ai_job_commands WHERE requested_by=? AND request_key=?",String.class,user,key);
  if(rows.isEmpty())return Optional.empty();
  UUID id=UUID.fromString(rows.getFirst());var command=find(id).orElseThrow();
  if(!Objects.equals(command.fingerprint(),fingerprint))throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key used with a different request");
  var job=jobs.get(id,user);
  if(job.status()==AIJobStatus.COMPLETED && job.result().type()==AIJobResultType.QUIZ && jdbc.queryForList("SELECT id FROM quizzes WHERE id=?",Long.class,Long.valueOf(job.result().id())).isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Generated Quiz was deleted. Use a new key.");
  return Optional.of(id);
 }
 public Optional<Command> find(UUID id) {
  return jdbc.query("SELECT c.*,j.course_id,j.document_id FROM ai_job_commands c JOIN ai_jobs j ON j.id=c.job_id WHERE c.job_id=?",(rs,n)->new Command(id,rs.getLong("requested_by"),rs.getLong("course_id"),rs.getObject("document_id",Long.class),rs.getString("mode"),rs.getString("request_json"),json.readValue(rs.getString("sources_json"),new TypeReference<Map<Long,String>>(){}),rs.getString("request_key"),rs.getString("fingerprint")),id.toString()).stream().findFirst();
 }
 public List<UUID> pendingIds(int limit) {return jdbc.queryForList("SELECT j.id FROM ai_jobs j JOIN ai_job_commands c ON c.job_id=j.id JOIN courses co ON co.id=j.course_id JOIN workspaces w ON w.id=co.workspace_id WHERE w.owner_id=j.requested_by AND j.status='PENDING' AND j.type IN ('EXTRACT_CONCEPTS','GENERATE_QUIZ') ORDER BY j.created_at,j.id LIMIT ?",String.class,limit).stream().map(UUID::fromString).toList();}
}
