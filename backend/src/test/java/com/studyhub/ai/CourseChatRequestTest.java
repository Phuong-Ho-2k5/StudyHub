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
}
