package com.studyhub.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.studyhub.ai.dto.CourseChatRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CourseChatRequestTest {
    final JsonMapper mapper=JsonMapper.builder().build();
    @ParameterizedTest @ValueSource(strings={"{\"question\":123}","{\"question\":true}",
        "{\"question\":\"Q\",\"topK\":1.9}","{\"question\":\"Q\",\"topK\":20.9}",
        "{\"question\":\"Q\",\"topK\":\"5\"}","{\"question\":\"Q\",\"topK\":true}"})
    void rejectsCoercedTypesBeforeTheyCanReachSearch(String body) {
        assertThrows(RuntimeException.class,()->mapper.readValue(body,CourseChatRequest.class));
    }
    @Test void preservesOmittedDefaultAndExplicitNullForValidation() {
        assertEquals(5,mapper.readValue("{\"question\":\"Q\"}",CourseChatRequest.class).getTopK());
        assertNull(mapper.readValue("{\"question\":\"Q\",\"topK\":null}",CourseChatRequest.class).getTopK());
        assertEquals(20,mapper.readValue("{\"question\":\"Q\",\"topK\":20}",CourseChatRequest.class).getTopK());
    }
    @ParameterizedTest @ValueSource(strings={"\"1\"","true","1.0","1.5","9223372036854775808","[]","{}"})
    void rejectsCoercedSessionIds(String value) {
        assertThrows(RuntimeException.class,()->mapper.readValue("{\"question\":\"Q\",\"sessionId\":"+value+"}",CourseChatRequest.class));
    }
    @Test void acceptsNullAndBothSmallAndLargeIntegerSessionIds() {
        assertNull(mapper.readValue("{\"question\":\"Q\"}",CourseChatRequest.class).getSessionId());
        assertNull(mapper.readValue("{\"question\":\"Q\",\"sessionId\":null}",CourseChatRequest.class).getSessionId());
        assertEquals(1L,mapper.readValue("{\"question\":\"Q\",\"sessionId\":1}",CourseChatRequest.class).getSessionId());
        assertEquals(Long.MAX_VALUE,mapper.readValue("{\"question\":\"Q\",\"sessionId\":9223372036854775807}",CourseChatRequest.class).getSessionId());
    }
}
