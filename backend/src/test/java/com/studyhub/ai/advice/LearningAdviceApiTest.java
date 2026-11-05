package com.studyhub.ai.advice;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:advice_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "studyhub.ai.url=http://127.0.0.1:1", "studyhub.ai.token=", "jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdoaWNoLWlzLWxvbmc="})
@ActiveProfiles("test")
class LearningAdviceApiTest extends AdviceApiSupport {
    @Test void validatesStrictInputsAndColdStartWithoutCallingAi() throws Exception {
        var owner=owner(); var other=owner();
        String path="/api/courses/"+owner.courseId()+"/ai/learning-advice";
        assertEquals(401,call("POST",path,null,Map.of("question","Help")).statusCode());
        assertEquals(404,call("POST",path,other.token(),Map.of("question","Help")).statusCode());
        assertEquals(404,call("POST","/api/courses/9223372036854775807/ai/learning-advice",owner.token(),Map.of("question","Help")).statusCode());
        for (Object invalid:List.of(Map.of(),Map.of("question",true),Map.of("question",123),Map.of("question","\u00a0"),
                Map.of("question","x".repeat(2001)),Map.of("question","Help","topK",0),Map.of("question","Help","topK",11),
                Map.of("question","Help","topK",1.2),Map.of("question","Help","topK",true),Map.of("question","Help","topK","5"),
                Map.of("question","Help","userId",owner.userId()),Map.of("question","Help","learningEvidence",List.of()))) {
            var response=call("POST",path,owner.token(),invalid);
            assertEquals(400,response.statusCode(),response.body());
        }
        var nullK=new HashMap<String,Object>();nullK.put("question","Help");nullK.put("topK",null);
        assertEquals(400,call("POST",path,owner.token(),nullK).statusCode());
        var response=call("POST",path,owner.token(),Map.of("question","  Help  "));
        assertEquals(200,response.statusCode(),response.body());
        var result=body(response);
        assertEquals("INSUFFICIENT_CONTEXT",result.path("status").asText());
        assertEquals("Help",result.path("question").asText());
        assertEquals(LearningAdviceModels.INSUFFICIENT_ANSWER,result.path("answer").asText());
        assertTrue(result.path("model").isNull());assertTrue(result.path("citations").isEmpty());
        assertTrue(result.path("learningEvidence").isEmpty());
        assertFalse(result.path("asOf").asText().isBlank());
        concept(owner,"Atomicity",20);
        assertEquals(503,call("POST",path,owner.token(),Map.of("question","Help")).statusCode());
    }
}
