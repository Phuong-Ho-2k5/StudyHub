package com.studyhub.document;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;

/** Runs the production multipart client against the local Postman simulation. */
@EnabledIfEnvironmentVariable(named = "AI_SIMULATION_URL", matches = ".+")
class DocumentExtractionClientTest {
    final byte[] pdf = "%PDF-1.7\ncontract-fixture".getBytes(StandardCharsets.UTF_8);

    DocumentExtractionClient client(String route, long timeout) {
        return new DocumentExtractionClient(System.getenv("AI_SIMULATION_URL") + "/" + route,
                "simulation-token", timeout);
    }

    @Test void sendsMultipartPdfAndRestoresTrustedSource() {
        var result = client("success", 10).extract(42L, "Bài học.pdf", pdf);
        assertEquals(42L, result.documentId());
        assertEquals("Bài học.pdf", result.source());
        assertEquals("StudyHub extracted text", result.pages().getFirst().text());
        assertEquals(1, result.pageCount());
        assertEquals(10, result.pages().getFirst().blocks().getFirst().bbox().l());
    }

    @ParameterizedTest
    @CsvSource({"invalid,502", "html-error,502", "unavailable,503", "timeout,504"})
    void translatesUpstreamFailuresWithoutLeakingDetails(String route, int status) {
        var error = assertThrows(ResponseStatusException.class,
                () -> client(route, 1).extract(42L, "lecture.pdf", pdf));
        assertEquals(status, error.getStatusCode().value());
        assertFalse(error.getReason().contains("<html>"));
    }
}
