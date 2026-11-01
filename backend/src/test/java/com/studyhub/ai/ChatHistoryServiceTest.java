package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.studyhub.ai.dto.CourseChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

class ChatHistoryServiceTest extends ChatHistoryTestSupport {
    @Autowired ChatHistoryService history;

    @Test void failureToInsertAssistantRollsBackUserAndSessionTimestamp() throws Exception {
        var f=fixture(); long id=session(f);
        var before=jdbc.queryForObject("SELECT updated_at FROM chat_sessions WHERE id=?",java.time.OffsetDateTime.class,id);
        // Real NOT NULL constraint failure after the USER insert, no repository stubbing.
        var invalid=new CourseChatResponse(f.course(),"Q","ANSWERED",null,"model","course-rag-v2",List.of(),null,null,null);
        assertThrows(DataIntegrityViolationException.class,()->history.appendTurn(f.course(),f.owner(),id,invalid));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=?",Integer.class,id));
        assertEquals(before,jdbc.queryForObject("SELECT updated_at FROM chat_sessions WHERE id=?",java.time.OffsetDateTime.class,id));
    }

    @Test void deletedSessionCannotBeRecreatedByAppendingAnAnswer() throws Exception {
        var f=fixture();long id=session(f);
        jdbc.update("DELETE FROM chat_sessions WHERE id=?",id);
        var answer=new CourseChatResponse(f.course(),"Q","INSUFFICIENT_CONTEXT","No evidence",null,"course-rag-v2",List.of(),null,null,null);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->history.appendTurn(f.course(),f.owner(),id,answer)).getStatusCode().value());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=?",Integer.class,id));
    }
}
