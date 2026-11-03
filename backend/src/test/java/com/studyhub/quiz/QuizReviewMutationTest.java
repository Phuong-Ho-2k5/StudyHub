package com.studyhub.quiz;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class QuizReviewMutationTest extends QuizReviewTestSupport {
    @Test void questionEditInvalidatesOnlyItsApprovalAndResetsConfirmation() throws Exception {
        var f=fixture(2); reviewAll(f,0); action(f,"confirm",0,200);
        long q=f.questions().getFirst();
        call("PUT","/api/questions/"+q,f.token(),question("Edited",0L),200);
        var state=get(f);
        assertEquals("DRAFT",state.at("/quiz/status").asText());
        assertEquals(1,state.path("contentRevision").asInt());
        assertEquals(1,state.path("reviewedQuestions").asInt());
        assertTrue(state.path("confirmedBy").isNull());
        assertTrue(state.path("questions").get(0).path("reviewedAt").isNull());
        assertTrue(state.path("questions").get(0).path("editedSinceGeneration").asBoolean());
        assertEquals("NONE",state.path("questions").get(0).path("evidenceOrigin").asText());
        assertFalse(state.path("canConfirm").asBoolean());
        action(f,"publish",0,409); action(f,"confirm",1,409);
        call("PUT","/api/questions/"+q,f.token(),question("Stale edit",0L),409);
        action(f,"questions/"+q+"/review",1,200); action(f,"confirm",1,200);
        call("PUT",f.path(),f.token(),Map.of("title","Retitled","status","CONFIRMED","expectedRevision",1),200);
        state=get(f); assertEquals(2,state.path("contentRevision").asInt());
        assertEquals(2,state.path("reviewedQuestions").asInt()); assertEquals("DRAFT",state.at("/quiz/status").asText());
        action(f,"confirm",2,200);action(f,"publish",2,200);
    }
    @Test void crudCannotBypassWorkflowOrEditPublishedAndArchivedQuizzes() throws Exception {
        var f=fixture(1); long q=f.questions().getFirst();
        for(String status:List.of("CONFIRMED","PUBLISHED","ARCHIVED"))
            call("PUT",f.path(),f.token(),Map.of("title","Jump","status",status,"expectedRevision",0),409);
        call("PUT",f.path(),f.token(),Map.of("title","Missing revision","status","DRAFT"),400);
        call("PUT","/api/questions/"+q,f.token(),question("Missing revision",null),400);
        call("POST",f.path()+"/questions",f.token(),question("Missing revision",null),400);
        call("DELETE","/api/questions/"+q,f.token(),null,400);
        reviewAll(f,0);action(f,"confirm",0,200);
        call("PUT",f.path(),f.token(),Map.of("title","Jump back","status","DRAFT","expectedRevision",0),409);
        action(f,"publish",0,200);
        for(String status:List.of("PUBLISHED","ARCHIVED")) {
            call("PUT",f.path(),f.token(),Map.of("title","Locked","status",status,"expectedRevision",0),409);
            call("PUT","/api/questions/"+q,f.token(),question("Locked",0L),409);
            call("POST",f.path()+"/questions",f.token(),question("Locked",0L),409);
            call("DELETE","/api/questions/"+q+"?expectedRevision=0",f.token(),null,409);
            if(status.equals("PUBLISHED"))action(f,"archive",0,200);
        }
        assertEquals("Question 0",get(f).at("/questions/0/question/text").asText());
    }
    @Test void additionsAndDeletionsRequireRevisionAndNeverConfirmAnEmptyQuiz() throws Exception {
        var f=fixture(1);reviewAll(f,0); action(f,"confirm",0,200);
        long added=call("POST",f.path()+"/questions",f.token(),question("Added",0L),201).path("id").asLong();
        assertEquals(1,get(f).path("reviewedQuestions").asInt());action(f,"confirm",1,409);
        call("DELETE","/api/questions/"+added+"?expectedRevision=0",f.token(),null,409);
        call("DELETE","/api/questions/"+added+"?expectedRevision=1",f.token(),null,204);
        action(f,"confirm",2,200);
        call("DELETE","/api/questions/"+f.questions().getFirst()+"?expectedRevision=2",f.token(),null,204);
        var state=get(f);assertEquals(3,state.path("contentRevision").asInt());
        assertEquals(0,state.path("totalQuestions").asInt());action(f,"confirm",3,409);
    }
    @Test void aiMcqValidationIsStrictAndInvalidMutationRollsBack() throws Exception {
        var f=fixture(1);reviewAll(f,0);action(f,"confirm",0,200);
        var bad=new LinkedHashMap<>(question("Bad",0L));
        bad.put("options",List.of(Map.of("text","A","correct",true),Map.of("text"," a ","correct",false),Map.of("text","C","correct",false),Map.of("text","D","correct",false)));
        call("PUT","/api/questions/"+f.questions().getFirst(),f.token(),bad,400);
        bad.put("type","TRUE_FALSE");bad.put("options",List.of(Map.of("text","True","correct",true),Map.of("text","False","correct",false)));
        call("PUT","/api/questions/"+f.questions().getFirst(),f.token(),bad,400);
        var state=get(f);assertEquals("CONFIRMED",state.at("/quiz/status").asText());
        assertEquals(0,state.path("contentRevision").asInt());assertEquals(1,state.path("reviewedQuestions").asInt());
        jdbc.update("UPDATE answer_options SET is_correct=true WHERE question_id=?",f.questions().getFirst());
        action(f,"publish",0,409);
    }
    @Test void malformedCrudRevisionCannotBeCoerced() throws Exception {
        var f=fixture(1);
        for(Object revision:List.of("0",0.25,false,-1)) {
            var body=question("Invalid revision",null);body.put("expectedRevision",revision);
            call("PUT","/api/questions/"+f.questions().getFirst(),f.token(),body,400);
            call("PUT",f.path(),f.token(),Map.of("title","Invalid","status","DRAFT","expectedRevision",revision),400);
        }
    }
}
