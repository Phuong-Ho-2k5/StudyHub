package com.studyhub.quiz;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class QuizReviewApiTest extends QuizReviewTestSupport {
    @Test void everyCurrentQuestionMustBeReviewedThenConfirmedThenPublished() throws Exception {
        var f=fixture(2); var initial=get(f);
        assertEquals(0,initial.path("contentRevision").asLong());
        assertFalse(initial.path("canConfirm").asBoolean());
        assertEquals(0,initial.path("reviewedQuestions").asInt());
        action(f,"publish",0,409);
        action(f,"questions/"+f.questions().getFirst()+"/review",0,200);
        action(f,"confirm",0,409);
        var reviewed=reviewAll(f,0); assertTrue(reviewed.path("canConfirm").asBoolean());
        assertEquals("DRAFT",reviewed.at("/quiz/status").asText());
        var confirmed=action(f,"confirm",0,200);
        assertEquals("CONFIRMED",confirmed.at("/quiz/status").asText());
        assertEquals(f.owner(),confirmed.path("confirmedBy").asLong());
        assertTrue(confirmed.path("canPublish").asBoolean());
        assertEquals(confirmed.path("confirmedAt"),action(f,"confirm",0,200).path("confirmedAt"));
        var answers=new ArrayList<Object>();
        for(var q:confirmed.path("questions")) answers.add(Map.of("questionId",q.at("/question/id").asLong(),"answerOptionIds",List.of(q.at("/question/options/0/id").asLong())));
        call("POST",f.path()+"/submit",f.token(),Map.of("answers",answers),400);
        var published=action(f,"publish",0,200);
        assertEquals("PUBLISHED",published.at("/quiz/status").asText());
        assertEquals(published.path("publishedAt"),action(f,"publish",0,200).path("publishedAt"));
        call("POST",f.path()+"/submit",f.token(),Map.of("answers",answers),200);
        action(f,"confirm",0,409);
        action(f,"archive",0,200); action(f,"archive",0,200); action(f,"publish",0,409);
    }
    @Test void scopeAndStrictRevisionInputAreEnforced() throws Exception {
        var f=fixture(1);var other=fixture(1);
        call("GET",f.path()+"/review",null,null,401);
        call("GET",f.path()+"/review",other.token(),null,404);
        for(String op:List.of("confirm","publish","archive","questions/"+f.questions().getFirst()+"/review","questions/"+f.questions().getFirst()+"/unreview")) {
            call("POST",f.path()+"/"+op,other.token(),Map.of("expectedRevision",0),404);
        }
        action(f,"questions/"+other.questions().getFirst()+"/review",0,404);
        for(Object body:List.of(Map.of(),Map.of("expectedRevision",-1),Map.of("expectedRevision","0"),Map.of("expectedRevision",0.5),Map.of("expectedRevision",false),Map.of("expectedRevision",0,"reviewedBy",f.owner())))
            call("POST",f.path()+"/confirm",f.token(),body,400);
        call("POST",f.path()+"/confirm",f.token(),Collections.singletonMap("expectedRevision",null),400);
        action(f,"questions/"+f.questions().getFirst()+"/review",1,409);
        jdbc.update("UPDATE quizzes SET source_type='MANUAL' WHERE id=?",f.quiz());
        call("GET",f.path()+"/review",f.token(),null,409);action(f,"confirm",0,409);
        call("PUT",f.path(),f.token(),Map.of("title","Manual","status","CONFIRMED"),409);
    }
    @Test void unreviewInvalidatesConfirmationAndOldTabs() throws Exception {
        var f=fixture(2); var reviewed=reviewAll(f,0);
        var first=reviewed.path("questions").get(0).path("reviewedAt");
        assertEquals(first,action(f,"questions/"+f.questions().getFirst()+"/review",0,200).path("questions").get(0).path("reviewedAt"));
        action(f,"confirm",0,200);
        var result=action(f,"questions/"+f.questions().getFirst()+"/unreview",0,200);
        assertEquals("DRAFT",result.at("/quiz/status").asText());
        assertEquals(1,result.path("contentRevision").asInt());
        assertEquals(1,result.path("reviewedQuestions").asInt());
        assertTrue(result.path("confirmedAt").isNull());
        action(f,"publish",0,409);action(f,"questions/"+f.questions().getFirst()+"/review",0,409);
        action(f,"questions/"+f.questions().getFirst()+"/review",1,200);
        action(f,"confirm",1,200);action(f,"publish",1,200);
        action(f,"questions/"+f.questions().getFirst()+"/unreview",1,409);
    }
    @Test void emptyQuizCannotBeConfirmed() throws Exception {var f=fixture(0); action(f,"confirm",0,409);}
}
