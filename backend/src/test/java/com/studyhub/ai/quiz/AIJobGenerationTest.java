package com.studyhub.ai.quiz;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.studyhub.ai.job.*;
class AIJobGenerationTest extends QuizGenerationApiSupport {
 @Autowired AIJobGenerationWorker worker;
 @Autowired AIJobService jobs;
 @Test void acceptedRequestDoesNotCallAiAndWorkerPublishesOnlyOnce() throws Exception {
  var f=fixture();String key=UUID.randomUUID().toString();int before=calls.get();
  var response=send("POST",path(f),f.token(),key,body());
  assertEquals(202,response.statusCode(),response.body());var accepted=json.readTree(response.body());
  var id=UUID.fromString(accepted.path("jobId").asText());
  assertEquals("/api/ai/jobs/"+id,response.headers().firstValue("Location").orElseThrow());
  assertEquals(before,calls.get());assertEquals(0,quizzes(f));
  assertEquals(AIJobStatus.PENDING,jobs.get(id,f.owner()).status());
  assertTrue(worker.process(id));
  if(System.getenv("QUIZ_PIPELINE_AI_URL")==null)assertEquals(before+1,calls.get());
  var completed=jobs.get(id,f.owner());assertEquals(AIJobStatus.COMPLETED,completed.status());assertEquals(AIJobResultType.QUIZ,completed.result().type());
  assertEquals(1,quizzes(f));assertFalse(worker.process(id));
  assertEquals(id.toString(),call("POST",path(f),f.token(),key,body(),202).path("jobId").asText());
 }
 @Test void changedSourceFailsWithoutCallingAiOrSavingQuiz() throws Exception {
  var f=fixture();var accepted=call("POST",path(f),f.token(),UUID.randomUUID().toString(),body(),202);
  var id=UUID.fromString(accepted.path("jobId").asText());int before=calls.get();
  jdbc.update("UPDATE documents SET published_ingestion_id=NULL WHERE id=?",f.document());
  assertTrue(worker.process(id));assertEquals(before,calls.get());assertEquals(0,quizzes(f));
  assertEquals(AIJobStatus.FAILED,jobs.get(id,f.owner()).status());assertEquals(AIJobErrorCode.SOURCE_CHANGED,jobs.get(id,f.owner()).errorCode());
 }
}
