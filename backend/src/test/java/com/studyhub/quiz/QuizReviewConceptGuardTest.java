package com.studyhub.quiz;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class QuizReviewConceptGuardTest extends QuizReviewTestSupport {
    @Test void deletingReferencedConceptCannotSilentlyChangeReviewedAiQuestions() throws Exception {
        var f=fixture(1);
        long concept=call("POST","/api/courses/"+f.course()+"/concepts",f.token(),Map.of("name","Atomicity","confidence",0,"status","NEW"),201).path("id").asLong();
        var body=question("Linked",0L);body.put("conceptId",concept);
        call("PUT","/api/questions/"+f.questions().getFirst(),f.token(),body,200);
        reviewAll(f,1);action(f,"confirm",1,200);action(f,"publish",1,200);
        call("DELETE","/api/concepts/"+concept,f.token(),null,409);
        assertEquals(concept,get(f).at("/questions/0/question/conceptId").asLong());
    }
}
