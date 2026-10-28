package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.studyhub.search.dto.CourseSearchResponse.SearchHitResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;

@EnabledIfEnvironmentVariable(named="AI_SIMULATION_URL",matches=".+")
class CourseChatSimulationTest {
    RagAnswerClient client(long timeout) { return new RagAnswerClient(System.getenv("AI_SIMULATION_URL")+"/success", "simulation-token", timeout,"gpt-4.1-mini"); }
    List<SearchHitResponse> hits() { return List.of(new SearchHitResponse(1L,2L,"DB",0,"Atomicity",1,1,.8)); }
    @Test void generatesOverAuthenticatedHttpWithSourceMetadata() {
        var result=client(10).answer("Question",hits());
        assertEquals("ANSWERED",result.status()); assertEquals("Atomicity",result.answer());
        assertEquals("course-rag-v1",result.promptVersion());
    }
    @Test void preservesInsufficientContextStatus() {
        assertEquals("INSUFFICIENT_CONTEXT",client(10).answer("[insufficient]",hits()).status());
    }
    @ParameterizedTest @CsvSource({"budget,422","invalid-context,502","unavailable,503","timeout,504","malformed,502"})
    void translatesFailuresOverRealHttp(String marker,int expected) {
        var error=assertThrows(ResponseStatusException.class,()->client(1).answer("["+marker+"]",hits()));
        assertEquals(expected,error.getStatusCode().value());
    }
}
