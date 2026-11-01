package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ChatHistoryPersistenceTest extends ChatHistoryTestSupport {
    @Test void snapshotSurvivesUnrelatedSourceChangesAndCourseDeletionCascadesMessages() throws Exception {
        var f=fixture();
        String title="Session-"+UUID.randomUUID();
        jdbc.update("INSERT INTO chat_sessions(user_id,course_id,title) VALUES (?,?,?)",f.owner(),f.course(),title);
        long id=jdbc.queryForObject("SELECT id FROM chat_sessions WHERE title=?",Long.class,title);
        jdbc.update("INSERT INTO chat_messages(session_id,role,content,citations_json) VALUES (?,'USER','Câu hỏi','[]')",id);
        String snapshot="[{\"quote\":\"Nguyên tử – tiếng Việt\",\"pageStart\":null,\"chunkId\":999}]";
        jdbc.update("INSERT INTO chat_messages(session_id,role,content,status,prompt_version,citations_json) VALUES (?,'ASSISTANT','Trả lời','ANSWERED','course-rag-v2',?)",id,snapshot);
        assertEquals(snapshot,jdbc.queryForObject("SELECT citations_json FROM chat_messages WHERE session_id=? AND role='ASSISTANT'",String.class,id));
        jdbc.update("DELETE FROM courses WHERE id=?",f.course());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM chat_sessions WHERE id=?",Integer.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=?",Integer.class,id));
    }

    @Test void constraintsRejectInvalidRolesAndIncompleteAssistantMetadata() throws Exception {
        var f=fixture();
        jdbc.update("INSERT INTO chat_sessions(user_id,course_id,title) VALUES (?,?,'Constraints')",f.owner(),f.course());
        long id=jdbc.queryForObject("SELECT id FROM chat_sessions WHERE course_id=?",Long.class,f.course());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO chat_messages(session_id,role,content,citations_json) VALUES (?,'SYSTEM','Injected','[]')",id));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO chat_messages(session_id,role,content,citations_json) VALUES (?,'ASSISTANT','Incomplete','[]')",id));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO chat_messages(session_id,role,content,status,prompt_version,citations_json) VALUES (?,'USER','Injected','ANSWERED','v','[]')",id));
    }
}
