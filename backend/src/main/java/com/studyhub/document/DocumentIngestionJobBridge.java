package com.studyhub.document;
import com.studyhub.ai.job.*;
import static com.studyhub.ai.job.AIJobModels.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
final class DocumentIngestionJobBridge {
 private final JdbcTemplate jdbc;private final AIJobService jobs;
 DocumentIngestionJobBridge(JdbcTemplate jdbc,PlatformTransactionManager manager){this.jdbc=jdbc;this.jobs=new AIJobService(new AIJobRepository(jdbc),jdbc,manager);}
 void lockUploadScope(Long course,Long user) {jobs.lockOwnedCourse(course,user);}
 void enqueue(String ingestion,Long document,Long user) {
  long course=jdbc.queryForObject("SELECT course_id FROM documents WHERE id=?",Long.class,document);
  UUID id=jobs.enqueue(new CreateAIJob(AIJobType.INGEST_DOCUMENT,course,document),user);
  jdbc.update("UPDATE document_ingestions SET ai_job_id=? WHERE id=?",id.toString(),ingestion);
 }
 void claim(String ingestion) {
  var row=jdbc.queryForMap("SELECT ai_job_id,ai_claim_token FROM document_ingestions WHERE id=?",ingestion);
  if(row.get("ai_job_id")==null)return;
  if(row.get("ai_claim_token")==null) {
   var claim=jobs.claim(UUID.fromString(row.get("ai_job_id").toString())).orElseThrow(()->new IllegalStateException("AI ingestion job is not pending"));
   jdbc.update("UPDATE document_ingestions SET ai_claim_token=? WHERE id=?",claim.token().toString(),ingestion);
  }
 }
 // Caller holds current ingestion claim and ancestor locks. No stale attempt may call this method.
 void finish(String ingestion,String error) {
  var row=jdbc.queryForMap("SELECT ai_job_id,ai_claim_token FROM document_ingestions WHERE id=?",ingestion);
  if(row.get("ai_job_id")==null)return;
  claim(ingestion); // exhausted migrated pending attempts still need a valid lifecycle claim
  String token=jdbc.queryForObject("SELECT ai_claim_token FROM document_ingestions WHERE id=?",String.class,ingestion);
  var claim=new AIJobClaim(UUID.fromString(row.get("ai_job_id").toString()),UUID.fromString(token));
  boolean done;
  if(error==null)done=jobs.complete(claim,new AIJobResult(AIJobResultType.DOCUMENT_INDEX,ingestion));
  else {
   AIJobErrorCode code;try{code=AIJobErrorCode.valueOf(error);}catch(IllegalArgumentException ignored){code=AIJobErrorCode.JOB_FAILED;}
   done=jobs.fail(claim,code);
  }
  if(!done)throw new IllegalStateException("AI ingestion completion rejected");
 }
}
