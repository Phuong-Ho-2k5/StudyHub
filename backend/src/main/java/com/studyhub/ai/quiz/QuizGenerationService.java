package com.studyhub.ai.quiz;

import static com.studyhub.ai.quiz.QuizGenerationModels.*;
import com.studyhub.ai.quiz.dto.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class QuizGenerationService {
    private final QuizGenerationSourceRepository sources;private final QuizGenerationRepository repository;
    private final QuizGenerationClient ai;private final QuizGenerationEvidenceValidator validator;private final ObjectMapper json;
    public QuizGenerationService(QuizGenerationSourceRepository sources,QuizGenerationRepository repository,QuizGenerationClient ai,QuizGenerationEvidenceValidator validator,ObjectMapper json) {
        this.sources=sources;this.repository=repository;this.ai=ai;this.validator=validator;this.json=json;
    }
    public GenerationResult generateDocument(Long document,Long user,String key,GenerateDocumentQuizRequest request) {
        checkKey(key);Long course=sources.documentCourse(document,user);
        return generate(course,List.of(document),user,key,fingerprint(List.of("document",document,request)),request.title(),request.questionCount(),request.startChunkIndex(),request.chunkLimit());
    }
    public GenerationResult generateCourse(Long course,Long user,String key,GenerateCourseQuizRequest request) {
        checkKey(key);sources.requireScope(course,request.documentIds(),user);
        return generate(course,request.documentIds(),user,key,fingerprint(List.of("course",course,request)),request.title(),request.questionCount(),0,request.chunkLimit());
    }
    private GenerationResult generate(Long course,List<Long> documents,Long user,String key,String fingerprint,String title,int count,int start,int limit) {
        var completed=repository.findCompleted(user,key,fingerprint);
        if(completed.isPresent())return new GenerationResult(false,completed.get());
        var window=sources.read(course,documents,user,start,limit);
        var result=validator.validate(window,ai.generate(window,count),count);
        try {return new GenerationResult(true,repository.saveDraft(window,result,user,key,fingerprint,title));}
        catch(DataIntegrityViolationException conflict) {
            // saveDraft has already rolled back; replay outside the failed PostgreSQL transaction.
            var winner=repository.findCompleted(user,key,fingerprint);
            if(winner.isPresent())return new GenerationResult(false,winner.get());throw conflict;
        }
    }
    public GenerationMetadata getGeneration(Long quiz,Long user) {return repository.getGeneration(quiz,user);}
    private void checkKey(String key) {
        if(key==null || !key.matches("[!-~]{1,100}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key must contain 1–100 visible ASCII characters without spaces.");
    }
    private String fingerprint(Object value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException error) {throw new IllegalStateException(error);}
    }
}
