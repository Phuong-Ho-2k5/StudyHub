package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ChatHistoryApiTest extends ChatHistoryTestSupport {
    @Test void createsTrimmedSessionAndListsOnlyTheSelectedCourseInStableOrder() throws Exception {
        var f=fixture();
        assertEquals(0,call("GET",sessions(f.course()),f.token(),null,200).path("totalElements").asInt());
        var first=call("POST",sessions(f.course()),f.token(),Map.of("title","  Ôn tập chương 1  "),201);
        assertEquals("Ôn tập chương 1",first.path("title").asText());
        assertEquals(f.course(),first.path("courseId").asLong());
        assertTrue(first.hasNonNull("createdAt")); assertTrue(first.hasNonNull("updatedAt"));
        long second=session(f);
        long otherCourse=course(f.token(),f.workspace());
        call("POST",sessions(otherCourse),f.token(),Map.of("title","Other course"),201);
        var page=call("GET",sessions(f.course())+"?size=1",f.token(),null,200);
        assertEquals(2,page.path("totalElements").asInt());
        assertEquals(second,page.path("content").get(0).path("id").asLong());
        assertEquals(first.path("id").asLong(),call("GET",sessions(f.course())+"?size=1&page=1",f.token(),null,200).path("content").get(0).path("id").asLong());
        assertEquals(0,call("GET",sessions(f.course())+"?page=99",f.token(),null,200).path("content").size());
        var messages=call("GET",sessions(f.course())+"/"+second+"/messages",f.token(),null,200);
        assertEquals(0,messages.path("totalElements").asInt());
        assertFalse(first.has("user")); assertFalse(first.has("passwordHash"));
    }

    @Test void historyRequiresJwtCourseOwnershipAndMatchingSessionScope() throws Exception {
        var owner=fixture(); var other=fixture(); long id=session(owner);
        String path=sessions(owner.course()), messages=path+"/"+id+"/messages";
        for(String route:List.of(path,messages)) {
            call("GET",route,null,null,401);
            call("GET",route,other.token(),null,404);
        }
        call("POST",path,null,Map.of("title","No JWT"),401);
        call("POST",path,other.token(),Map.of("title","Foreign"),404);
        long secondCourse=course(owner.token(),owner.workspace());
        call("GET",sessions(secondCourse)+"/"+id+"/messages",owner.token(),null,404);
        call("GET",path+"/9223372036854775807/messages",owner.token(),null,404);
        call("GET",sessions(9223372036854775807L),owner.token(),null,404);
    }

    @Test void validatesTitleWithoutCoercionAndRejectsInvalidPaging() throws Exception {
        var f=fixture(); String path=sessions(f.course()); long id=session(f);
        for(Object body:List.of(Map.of(),Map.of("title"," "),Map.of("title","x".repeat(151)),Map.of("title",123),Map.of("title",true),Map.of("title",List.of("x"))))
            call("POST",path,f.token(),body,400);
        var nullTitle=new HashMap<String,Object>(); nullTitle.put("title",null);
        call("POST",path,f.token(),nullTitle,400);
        for(String route:List.of(path,path+"/"+id+"/messages")) {
            for(String query:List.of("page=-1","size=0","size=101","page=abc","size=abc","sort=title,asc","page=2147483647&size=20"))
                call("GET",route+"?"+query,f.token(),null,400);
        }
    }
}
