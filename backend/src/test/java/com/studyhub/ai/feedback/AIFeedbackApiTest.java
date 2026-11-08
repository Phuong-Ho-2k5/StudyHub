package com.studyhub.ai.feedback;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AIFeedbackApiTest extends AIFeedbackTestSupport {
    @Test void createsThenUpdatesSingleFeedback() throws Exception {
        var f=fixture();var session=jdbc.queryForMap("select * from chat_sessions where id=?",f.session());
        var message=jdbc.queryForMap("select * from chat_messages where id=?",f.assistant());
        var a=save(f,Map.of("rating","HELPFUL","note","  Good\nexplanation  "),201);
        assertEquals("Good\nexplanation",a.path("note").asText());assertEquals(f.assistant(),a.path("messageId").asLong());
        var b=save(f,Map.of("rating","NOT_HELPFUL","note","More examples"),200);
        assertEquals(a.path("id"),b.path("id"));assertEquals(a.path("createdAt"),b.path("createdAt"));
        assertEquals("NOT_HELPFUL",b.path("rating").asText());assertEquals("More examples",b.path("note").asText());
        assertEquals(1,jdbc.queryForObject("select count(*) from ai_feedback where message_id=?",Integer.class,f.assistant()));
        assertEquals(session,jdbc.queryForMap("select * from chat_sessions where id=?",f.session()));
        assertEquals(message,jdbc.queryForMap("select * from chat_messages where id=?",f.assistant()));
        assertFalse(b.has("userId"));
    }
    @Test void identicalRetryPreservesTimestampsAndOmittedNoteClearsPreviousNote() throws Exception {
        var f=fixture();var body=Map.of("rating","HELPFUL","note","test");
        var a=save(f,body,201);assertEquals(a,save(f,body,200));
        assertTrue(save(f,Map.of("rating","HELPFUL"),200).path("note").isNull());
        assertTrue(save(f,Map.of("rating","HELPFUL","note"," \n "),200).path("note").isNull());
    }
    @Test void validatesStrictPayload() throws Exception {
        var f=fixture();
        for(String payload:List.of("{}","null","[]","{","{\"rating\":null}","{\"rating\":1}","{\"rating\":true}",
                "{\"rating\":\"helpful\"}","{\"rating\":\"UNKNOWN\"}","{\"rating\":\"HELPFUL\",\"note\":1}",
                "{\"rating\":\"HELPFUL\",\"note\":true}","{\"rating\":\"HELPFUL\",\"note\":{}}",
                "{\"rating\":\"HELPFUL\",\"note\":[]}","{\"rating\":\"HELPFUL\",\"userId\":1}")) {
            var r=raw("POST",feedback(f.assistant()),f.token(),payload);assertEquals(400,r.statusCode(),payload+" "+r.body());
            assertTrue(json.readTree(r.body()).has("code"),r.body());assertFalse(r.body().contains("SQLException"));
        }
        save(f,Map.of("rating","HELPFUL","note","x".repeat(2001)),400);
        save(f,Map.of("rating","HELPFUL","note","  "+"x".repeat(2000)+"  "),201);
        save(f,Map.of("rating","HELPFUL","note","😀".repeat(1001)),400);
        var nullable=new HashMap<String,Object>();nullable.put("rating","HELPFUL");nullable.put("note",null);
        assertTrue(save(f,nullable,200).path("note").isNull());
    }
    @Test void rejectsForeignMessageBeforeRoleCheck() throws Exception {
        var f=fixture();var other=fixture();var body=Map.of("rating","HELPFUL");
        call("POST",feedback(f.assistant()),null,body,401);call("POST",feedback(f.assistant()),"bad",body,401);
        call("POST",feedback(Long.MAX_VALUE),f.token(),body,404);
        call("POST",feedback(other.assistant()),f.token(),body,404);
        call("POST",feedback(other.userMessage()),f.token(),body,404);
        call("POST",feedback(f.userMessage()),f.token(),body,400);
        jdbc.update("update chat_sessions set user_id=? where id=?",other.user(),f.session());
        save(f,body,404);
        assertEquals(0,jdbc.queryForObject("select count(*) from ai_feedback where message_id in (?,?)",Integer.class,f.assistant(),other.assistant()));
    }
}
