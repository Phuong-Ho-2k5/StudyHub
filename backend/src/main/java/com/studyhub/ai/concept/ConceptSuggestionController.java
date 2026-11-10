package com.studyhub.ai.concept;

import static com.studyhub.ai.concept.ConceptSuggestionModels.*;
import com.studyhub.ai.concept.dto.*;
import com.studyhub.security.CustomUserDetails;
import com.studyhub.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/documents/{documentId}/ai/concept-suggestions")
@Tag(name="AI Concept Suggestions")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode="400",description="Invalid request or unknown fields",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
    @ApiResponse(responseCode="401",description="Missing or invalid JWT",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
    @ApiResponse(responseCode="404",description="Document or draft outside current user's scope",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
    @ApiResponse(responseCode="409",description="Source changed, duplicate Concept name or confirmation conflict",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
})
public class ConceptSuggestionController {
    private final ConceptSuggestionService service;
    private final com.studyhub.ai.job.AIJobExecutionService async;
    public ConceptSuggestionController(ConceptSuggestionService service,com.studyhub.ai.job.AIJobExecutionService async) { this.service=service;this.async=async; }
    @PostMapping

    @ApiResponses({
        @ApiResponse(responseCode="202",description="Accepted; Location points to job status"),
        @ApiResponse(responseCode="422",description="Source window is empty or outside the document")
    })
    @Operation(summary="Generate sourced Concept drafts",description="Queues a draft, never creates Concepts. AI failures are exposed by the job status API. Uses an explicit published chunk window.")
    public ResponseEntity<com.studyhub.ai.job.AIJobAcceptedResponse> generate(@PathVariable Long courseId,@PathVariable Long documentId,
        @RequestBody GenerateConceptSuggestionsRequest request,@AuthenticationPrincipal CustomUserDetails user) {
        return async.submitConcept(courseId,documentId,user.getId(),request).accepted();
    }
    @GetMapping("/{batchId}")
    @Operation(summary="Read a saved draft",description="Snapshot remains readable after re-ingest with sourceCurrent=false; stale pending items cannot be confirmed.")
    public DraftBatch get(@PathVariable Long courseId,@PathVariable Long documentId,@PathVariable String batchId,
        @AuthenticationPrincipal CustomUserDetails user) {
        return service.getBatch(courseId,documentId,user.getId(),batchId);
    }
    @PostMapping("/{batchId}/items/{suggestionId}/confirm")
    @ApiResponses({@ApiResponse(responseCode="201",description="Created Concept"),@ApiResponse(responseCode="200",description="Same-content retry; original Concept returned")})
    @Operation(summary="Confirm a reviewed suggestion",description="Creates a NEW Concept with confidence 0. Returns 201 initially, 200 for same-content retry; conflicts and stale sources return 409.")
    public ResponseEntity<ConfirmationResponse> confirm(@PathVariable Long courseId,@PathVariable Long documentId,
        @PathVariable String batchId,@PathVariable Long suggestionId,@RequestBody ConfirmConceptSuggestionRequest request,
        @AuthenticationPrincipal CustomUserDetails user) {
        var result=service.confirm(courseId,documentId,user.getId(),batchId,suggestionId,request);
        return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(result.response());
    }
}
