package com.studyhub.quiz;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;

@EnabledIfEnvironmentVariable(named="PGVECTOR_TEST_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="RUN_QUIZ_REVIEW_COLLECTION",matches="1")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class QuizReviewCollectionIT extends QuizReviewPostgresSupport {
    static final String SCHEMA="review_collection_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) throws Exception {database(r,SCHEMA);}
    @AfterAll static void drop() throws Exception {cleanup(SCHEMA);}
    @Test void postmanExercisesRealWorkflowWithDisposableTokensAndFixtures() throws Exception {
        var f=fixture(2);var foreign=fixture(1);var empty=fixture(0);var stale=fixture(1);
        var vars=new LinkedHashMap<String,String>();vars.put("baseUrl","http://localhost:"+port);
        vars.put("accessToken",f.token());vars.put("quizId",""+f.quiz());
        vars.put("firstQuestionId",""+f.questions().get(0));vars.put("secondQuestionId",""+f.questions().get(1));
        vars.put("foreignQuizId",""+foreign.quiz());vars.put("foreignQuestionId",""+foreign.questions().getFirst());
        vars.put("emptyQuizId",""+empty.quiz());vars.put("emptyAccessToken",empty.token());
        vars.put("staleQuizId",""+stale.quiz());vars.put("staleAccessToken",stale.token());
        Path root=Path.of("..").toAbsolutePath().normalize();
        Path environment=Files.createTempFile("quiz-review-",".environment.yaml");
        Path output=root.resolve(".superpowers/sdd/2026-11-03-m7-t04-ai-quiz-review/collection.log");
        Files.createDirectories(output.getParent());
        try {
            JSON.writeValue(environment.toFile(),Map.of("name","Disposable Review fixtures","values",vars.entrySet().stream()
                    .map(e->Map.of("key",e.getKey(),"value",e.getValue(),"enabled",true,"type",e.getKey().toLowerCase().contains("token")?"secret":"default")).toList()));
            var process=new ProcessBuilder("postman","collection","run",root.resolve("infra/testing/quiz-review/collection").toString(),"--environment",environment.toString())
                    .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();fail("Collection timed out");}
            assertEquals(0,process.exitValue(),"See local review collection log");
            assertEquals("ARCHIVED",get(f).at("/quiz/status").asText());
            assertEquals(1,get(stale).path("contentRevision").asLong());
        } finally {Files.deleteIfExists(environment);}
    }
}
