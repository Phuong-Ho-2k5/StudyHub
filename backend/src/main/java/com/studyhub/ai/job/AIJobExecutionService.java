package com.studyhub.ai.job;
import static com.studyhub.ai.job.AIJobModels.*;
import com.studyhub.ai.concept.*;
import com.studyhub.ai.concept.dto.*;
import com.studyhub.ai.quiz.*;
import com.studyhub.ai.quiz.dto.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
@Service
public class AIJobExecutionService {
 private final AIJobCommandRepository commands;private final AIJobService jobs;private final ObjectMapper json;private final TransactionTemplate tx;
 private final ConceptSuggestionRepository concepts;private final ConceptExtractionClient conceptAi;private final ConceptEvidenceValidator conceptValidator;
 private final QuizGenerationSourceRepository sources;private final QuizGenerationRepository quizzes;private final QuizGenerationClient quizAi;private final QuizGenerationEvidenceValidator quizValidator;
 public AIJobExecutionService(AIJobCommandRepository commands,AIJobService jobs,ObjectMapper json,PlatformTransactionManager manager,
  ConceptSuggestionRepository concepts,ConceptExtractionClient conceptAi,ConceptEvidenceValidator conceptValidator,
  QuizGenerationSourceRepository sources,QuizGenerationRepository quizzes,QuizGenerationClient quizAi,QuizGenerationEvidenceValidator quizValidator) {
  this.commands=commands;this.jobs=jobs;this.json=json;this.tx=new TransactionTemplate(manager);this.concepts=concepts;this.conceptAi=conceptAi;this.conceptValidator=conceptValidator;
  this.sources=sources;this.quizzes=quizzes;this.quizAi=quizAi;this.quizValidator=quizValidator;
 }
 public AIJobAcceptedResponse submitConcept(Long course,Long document,Long user,GenerateConceptSuggestionsRequest request) {
  var w=concepts.readWindow(course,document,user,request.startChunkIndex(),request.chunkLimit());
  return AIJobAcceptedResponse.of(commands.enqueue(new CreateAIJob(AIJobType.EXTRACT_CONCEPTS,course,document),user,"CONCEPTS",json.writeValueAsString(request),Map.of(document,w.publishedIngestionId()),null,null));
 }
 public AIJobAcceptedResponse submitDocumentQuiz(Long document,Long user,String key,GenerateDocumentQuizRequest request) {
  checkKey(key);long course=sources.documentCourse(document,user);
  return submitQuiz(course,document,user,key,fingerprint(List.of("document",document,request)),"DOCUMENT_QUIZ",request,List.of(document),request.startChunkIndex(),request.chunkLimit());
 }
 public AIJobAcceptedResponse submitCourseQuiz(Long course,Long user,String key,GenerateCourseQuizRequest request) {
  checkKey(key);sources.requireScope(course,request.documentIds(),user);
  return submitQuiz(course,null,user,key,fingerprint(List.of("course",course,request)),"COURSE_QUIZ",request,request.documentIds(),0,request.chunkLimit());
 }
 private AIJobAcceptedResponse submitQuiz(Long course,Long document,Long user,String key,String fp,String mode,Object request,List<Long> documents,int start,int limit) {
  // Replay precedes source-window validation: a completed snapshot survives later re-ingestion.
  var existing=commands.replay(user,key,fp);if(existing.isPresent())return AIJobAcceptedResponse.of(existing.get());
  var legacy=quizzes.findCompleted(user,key,fp);
  if(legacy.isPresent())return tx.execute(status->{
   UUID id=commands.enqueue(new CreateAIJob(AIJobType.GENERATE_QUIZ,course,document),user,mode,json.writeValueAsString(request),Map.of(),key,fp);
   if(jobs.get(id,user).status()==AIJobStatus.PENDING) {
    var claim=jobs.claim(id).orElseThrow();if(!jobs.complete(claim,new AIJobResult(AIJobResultType.QUIZ,legacy.get().quiz().id().toString())))throw new IllegalStateException("Legacy replay rejected");
   }
   return AIJobAcceptedResponse.of(id);
  });
  var w=sources.read(course,documents,user,start,limit);var versions=new LinkedHashMap<Long,String>();w.coverage().forEach(c->versions.put(c.documentId(),c.ingestionId()));
  return AIJobAcceptedResponse.of(commands.enqueue(new CreateAIJob(AIJobType.GENERATE_QUIZ,course,document),user,mode,json.writeValueAsString(request),versions,key,fp));
 }
 public void execute(AIJobClaim claim,AIJobCommandRepository.Command c) {
  if(c.mode().equals("CONCEPTS")) {
   var request=json.readValue(c.requestJson(),GenerateConceptSuggestionsRequest.class);
   var w=concepts.readWindow(c.courseId(),c.documentId(),c.userId(),request.startChunkIndex(),request.chunkLimit());
   verify(c.sources(),Map.of(c.documentId(),w.publishedIngestionId()));
   var result=conceptValidator.validate(w,conceptAi.extract(w,request.maxSuggestions()),request.maxSuggestions());
   jobs.publish(claim,()->new AIJobResult(AIJobResultType.CONCEPT_SUGGESTION_BATCH,concepts.saveDraft(c.courseId(),c.documentId(),c.userId(),w,result).id()));
  } else {
   String title;int count,start,limit;List<Long> documents;
   if(c.mode().equals("DOCUMENT_QUIZ")) {
    var r=json.readValue(c.requestJson(),GenerateDocumentQuizRequest.class);title=r.title();count=r.questionCount();start=r.startChunkIndex();limit=r.chunkLimit();documents=List.of(c.documentId());
   } else if(c.mode().equals("COURSE_QUIZ")) {
    var r=json.readValue(c.requestJson(),GenerateCourseQuizRequest.class);title=r.title();count=r.questionCount();start=0;limit=r.chunkLimit();documents=r.documentIds();
   } else throw new IllegalArgumentException("Unsupported command mode");
   var w=sources.read(c.courseId(),documents,c.userId(),start,limit);var versions=new LinkedHashMap<Long,String>();w.coverage().forEach(v->versions.put(v.documentId(),v.ingestionId()));verify(c.sources(),versions);
   var result=quizValidator.validate(w,quizAi.generate(w,count),count);
   jobs.publish(claim,()->new AIJobResult(AIJobResultType.QUIZ,quizzes.saveDraft(w,result,c.userId(),c.key(),c.fingerprint(),title).quiz().id().toString()));
  }
 }
 private void verify(Map<Long,String> expected,Map<Long,String> actual) {if(!expected.equals(actual))throw new ResponseStatusException(HttpStatus.CONFLICT,"Source changed");}
 private void checkKey(String key) {if(key==null || !key.matches("[!-~]{1,100}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Idempotency-Key");}
 private String fingerprint(Object value) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
