package com.studyhub.document;

import com.studyhub.document.dto.DocumentIngestionResponse;
import com.studyhub.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.studyhub.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
@Tag(name="Document")
@SecurityRequirement(name="bearerAuth")
public class DocumentIngestionController {
    private final DocumentIngestionService ingestion;
    public DocumentIngestionController(DocumentIngestionService ingestion) { this.ingestion = ingestion; }

    @PostMapping("/{documentId}/ingest")
    @Operation(summary="Queue PDF ingestion",description="Returns immediately with an owned AIJob. Published index remains available during rebuild.")
    @ApiResponses({@ApiResponse(responseCode="202",description="Accepted; Location points to job status"),@ApiResponse(responseCode="401",description="Missing or invalid JWT"),@ApiResponse(responseCode="404",description="Document outside scope"),@ApiResponse(responseCode="409",description="Ingestion already active or PDF not uploaded")})
    public org.springframework.http.ResponseEntity<com.studyhub.ai.job.AIJobAcceptedResponse> ingest(@PathVariable Long documentId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return com.studyhub.ai.job.AIJobAcceptedResponse.of(ingestion.enqueue(documentId,currentUser.getId())).accepted();
    }
}
