package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;

/** Exercises the production HTTP client against the contract-backed Postman simulation. */
@EnabledIfEnvironmentVariable(named = "AI_SIMULATION_URL", matches = ".+")
class DocumentEmbeddingSimulationTest {
    static final String MODEL = "intfloat/multilingual-e5-small@614241f622f53c4eeff9890bdc4f31cfecc418b3";

    DocumentEmbeddingClient client(String route, long timeout) {
        return new DocumentEmbeddingClient(System.getenv("AI_SIMULATION_URL") + "/" + route,
                "simulation-token", timeout, MODEL, 384);
    }

    @Test void sendsAuthenticatedJsonAndReceivesOrderedVectorBatch() {
        var result = client("success", 10).embed(List.of("StudyHub\nHọc toán", "Học vật lý"));
        assertEquals(MODEL, result.model());
        assertEquals(384, result.dimensions());
        assertEquals(2, result.vectors().size());
        assertEquals(384, result.vectors().getFirst().size());
        assertEquals(1., result.vectors().getFirst().getFirst());
    }

    @ParameterizedTest
    @CsvSource({"embed-invalid,502", "html-error,502", "unavailable,503", "timeout,504"})
    void rejectsMalformedResponsesAndTranslatesUpstreamFailures(String route, int status) {
        var error = assertThrows(ResponseStatusException.class, () -> client(route, 1).embed(List.of("Nội dung")));
        assertEquals(status, error.getStatusCode().value());
        assertFalse(error.getReason().contains("<html>"));
    }
}
