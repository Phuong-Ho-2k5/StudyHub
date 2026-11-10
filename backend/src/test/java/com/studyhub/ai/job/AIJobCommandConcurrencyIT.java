package com.studyhub.ai.job;
import static org.junit.jupiter.api.Assertions.*;
import static com.studyhub.ai.job.AIJobModels.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
class AIJobCommandConcurrencyIT extends AIJobPostgresSupport {
 static final String schema="command_race_"+UUID.randomUUID().toString().replace("-","");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r) throws Exception {database(r,schema);}
 @AfterAll static void drop() throws Exception {cleanup(schema);}
 @Autowired AIJobCommandRepository commands;
 @Autowired AIJobService jobs;
 @Test void concurrentReservationsReturnOneJobAndOneCommand() throws Exception {
  var f=fixture();String key=UUID.randomUUID().toString();var start=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)) {
   Callable<UUID> submit=()->{start.await();return commands.enqueue(new CreateAIJob(AIJobType.GENERATE_QUIZ,f.course(),f.document()),f.user(),"DOCUMENT_QUIZ","{}",Map.of(f.document(),"version"),key,"fingerprint");};
   var a=pool.submit(submit);var b=pool.submit(submit);start.countDown();assertEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
  }
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ai_job_commands WHERE requested_by=?",Integer.class,f.user()));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ai_jobs WHERE requested_by=?",Integer.class,f.user()));
 }
 @Test void staleOwnershipCommandsCannotStarveNewPendingWork() {
  var stale=fixture();var nextOwner=fixture();
  for(int i=0;i<20;i++) {
   UUID id=commands.enqueue(new CreateAIJob(AIJobType.EXTRACT_CONCEPTS,stale.course(),stale.document()),stale.user(),"CONCEPTS","{}",Map.of(),null,null);
   jdbc.update("UPDATE ai_jobs SET created_at=created_at - INTERVAL '1 day' WHERE id=?",id.toString());
  }
  jdbc.update("UPDATE workspaces SET owner_id=? WHERE id=?",nextOwner.user(),stale.workspace());
  var valid=fixture();UUID id=commands.enqueue(new CreateAIJob(AIJobType.EXTRACT_CONCEPTS,valid.course(),valid.document()),valid.user(),"CONCEPTS","{}",Map.of(),null,null);
  assertTrue(commands.pendingIds(20).contains(id),"Old unclaimable commands must not fill every worker batch");
 }
 @Test void rejectedCompletionRollsBackOutputAndLeavesClaimProcessing() {
  var f=fixture();var id=jobs.enqueue(new CreateAIJob(AIJobType.GENERATE_QUIZ,f.course(),null),f.user());var claim=jobs.claim(id).orElseThrow();
  assertThrows(IllegalArgumentException.class,()->jobs.publish(claim,()->{
   jdbc.update("INSERT INTO quizzes(course_id,title) VALUES (?,'Should roll back')",f.course());
   return new AIJobResult(AIJobResultType.DOCUMENT_INDEX,"wrong-type");
  }));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM quizzes WHERE course_id=?",Integer.class,f.course()));
  assertEquals(AIJobStatus.PROCESSING,jobs.get(id,f.user()).status());
 }
}
