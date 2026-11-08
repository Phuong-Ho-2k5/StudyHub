package com.studyhub.ai.feedback;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;

class AIFeedbackPersistenceTest extends AIFeedbackTestSupport {
    @Test void databaseEnforcesUniqueRatingAndForeignKeys() throws Exception {
        var f=fixture();save(f,Map.of("rating","HELPFUL"),201);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into ai_feedback(user_id,message_id,rating,created_at,updated_at) values (?,?,'HELPFUL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",f.user(),f.assistant()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update ai_feedback set rating='BAD' where message_id=?",f.assistant()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update ai_feedback set message_id=? where message_id=?",Long.MAX_VALUE,f.assistant()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update ai_feedback set user_id=? where message_id=?",Long.MAX_VALUE,f.assistant()));
    }
    @ParameterizedTest @ValueSource(strings={"message","session","course","user"})
    void deletionCascades(String target) throws Exception {
        var f=fixture();save(f,Map.of("rating","HELPFUL"),201);
        String table=switch(target){case "message"->"chat_messages";case "session"->"chat_sessions";case "course"->"courses";default->"users";};
        long id=switch(target){case "message"->f.assistant();case "session"->f.session();case "course"->f.course();default->f.user();};
        jdbc.update("delete from "+table+" where id=?",id);
        assertEquals(0,jdbc.queryForObject("select count(*) from ai_feedback where message_id=?",Integer.class,f.assistant()));
    }
}
