package com.studyhub.ai.job;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
@Component
public class AIJobGenerationWorker {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AIJobGenerationWorker.class);
 private final AIJobCommandRepository commands;private final AIJobService jobs;private final AIJobExecutionService execution;private final boolean enabled;
 public AIJobGenerationWorker(AIJobCommandRepository commands,AIJobService jobs,AIJobExecutionService execution,@Value("${studyhub.ai.jobs.worker-enabled:true}") boolean enabled){this.commands=commands;this.jobs=jobs;this.execution=execution;this.enabled=enabled;}
 @Scheduled(fixedDelayString="${studyhub.ai.jobs.poll-delay-ms:1000}",initialDelayString="${studyhub.ai.jobs.poll-delay-ms:1000}")
 public void tick(){if(enabled)try{processNext();}catch(RuntimeException error){log.warn("AI generation worker deferred work; durable state retained");}}
 public boolean processNext(){for(UUID id:commands.pendingIds(20))if(process(id))return true;return false;}
 public boolean process(UUID id) {
  var command=commands.find(id);if(command.isEmpty())return false;
  var claim=jobs.claim(id);if(claim.isEmpty())return false;
  try{execution.execute(claim.get(),command.get());}
  catch(RuntimeException error){
   AIJobErrorCode code=AIJobErrorCode.JOB_FAILED;
   if(error instanceof ResponseStatusException status)code=switch(status.getStatusCode().value()){
    case 404,409->AIJobErrorCode.SOURCE_CHANGED;case 422->AIJobErrorCode.NO_CONTENT;case 502->AIJobErrorCode.AI_INVALID_RESPONSE;case 503->AIJobErrorCode.AI_UNAVAILABLE;case 504->AIJobErrorCode.AI_TIMEOUT;default->AIJobErrorCode.JOB_FAILED;};
   jobs.fail(claim.get(),code);
  }
  return true;
 }
}
