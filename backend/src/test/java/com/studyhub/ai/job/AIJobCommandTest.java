package com.studyhub.ai.job;
import static org.junit.jupiter.api.Assertions.*;
import static com.studyhub.ai.job.AIJobModels.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
class AIJobCommandTest extends AIJobTestSupport {
 @Autowired AIJobCommandRepository commands;
 @Autowired AIJobService jobs;
 @Test void durableInputAndReservationSurviveReloadAndConflict() {
  var f=fixture(); String key=UUID.randomUUID().toString();
  var id=commands.enqueue(new CreateAIJob(AIJobType.GENERATE_QUIZ,f.course(),f.document()),f.user(),"DOCUMENT_QUIZ","{\"title\":\"Quiz\",\"questionCount\":2}",Map.of(f.document(),"source-1"),key,"fingerprint");
  var replay=commands.enqueue(new CreateAIJob(AIJobType.GENERATE_QUIZ,f.course(),f.document()),f.user(),"DOCUMENT_QUIZ","{}",Map.of(),key,"fingerprint");
  assertEquals(id,replay); var command=commands.find(id).orElseThrow();
  assertTrue(command.requestJson().contains("questionCount")); assertEquals("source-1",command.sources().get(f.document()));
  assertTrue(commands.pendingIds(100).contains(id));
  var error=assertThrows(ResponseStatusException.class,()->commands.enqueue(new CreateAIJob(AIJobType.GENERATE_QUIZ,f.course(),f.document()),f.user(),"DOCUMENT_QUIZ","{}",Map.of(),key,"other"));
  assertEquals(409,error.getStatusCode().value());
  jobs.claim(id).orElseThrow();assertFalse(commands.pendingIds(100).contains(id));
 }
 @Test void rollbackRemovesCommandAndJob() {
  var f=fixture(); UUID[] id={null};
  tx().executeWithoutResult(status->{id[0]=commands.enqueue(new CreateAIJob(AIJobType.EXTRACT_CONCEPTS,f.course(),f.document()),f.user(),"CONCEPTS","{}",Map.of(),null,null);status.setRollbackOnly();});
  assertTrue(commands.find(id[0]).isEmpty());
  assertEquals(404,assertThrows(ResponseStatusException.class,()->jobs.get(id[0],f.user())).getStatusCode().value());
 }
}
