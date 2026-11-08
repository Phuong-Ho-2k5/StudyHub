package com.studyhub.ai.feedback;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="RUN_FEEDBACK_COLLECTION",matches="1")
class AIFeedbackCollectionTest extends AIFeedbackTestSupport {
    @Test void runsSavedAssertionsAgainstRealSpringHttp() throws Exception {
        var f=fixture();var foreign=fixture();
        var vars=new LinkedHashMap<String,String>();
        vars.put("baseUrl","http://localhost:"+port);vars.put("accessToken",f.token());
        vars.put("messageId",""+f.assistant());vars.put("userMessageId",""+f.userMessage());
        vars.put("foreignMessageId",""+foreign.assistant());vars.put("courseId",""+f.course());vars.put("sessionId",""+f.session());
        Path root=Path.of("..").toAbsolutePath().normalize(),environment=Files.createTempFile("feedback-",".environment.yaml");
        Path log=root.resolve("logs/feedback/collection.log");Files.createDirectories(log.getParent());
        try {
            json.writeValue(environment.toFile(),Map.of("name","Disposable feedback fixtures","values",vars.entrySet().stream()
                .map(e->Map.of("key",e.getKey(),"value",e.getValue(),"enabled",true,"type",e.getKey().contains("Token")?"secret":"default")).toList()));
            var process=new ProcessBuilder("postman","collection","run",root.resolve("infra/testing/feedback/collection").toString(),
                "--environment",environment.toString(),"--no-report-events").directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();fail("Collection timeout; see local log");}
            assertEquals(0,process.exitValue(),"See logs/feedback/collection.log");
            assertEquals(1,jdbc.queryForObject("select count(*) from ai_feedback where message_id=?",Integer.class,f.assistant()));
            assertEquals(0,jdbc.queryForObject("select count(*) from ai_feedback where message_id in (?,?)",Integer.class,f.userMessage(),foreign.assistant()));
            var doc=call("GET","/v3/api-docs",null,null,200);
            assertTrue(doc.path("paths").path("/api/ai/messages/{messageId}/feedback").has("post"));
        } finally {Files.deleteIfExists(environment);}
    }
}
