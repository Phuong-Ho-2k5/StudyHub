package com.studyhub.ai.quiz;

import static com.studyhub.ai.quiz.QuizGenerationModels.*;
import com.studyhub.ai.quiz.dto.*;
import com.studyhub.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name="AI Quiz Generation")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({@ApiResponse(responseCode="400",description="Invalid input or Idempotency-Key"),
    @ApiResponse(responseCode="401",description="Missing or invalid JWT"),@ApiResponse(responseCode="404",description="Resource outside user's scope"),
    @ApiResponse(responseCode="409",description="Source changed, missing published content or idempotency conflict"),
    @ApiResponse(responseCode="422",description="Source window is empty")})
public class QuizGenerationController {
    private final QuizGenerationService service;
    private final com.studyhub.ai.job.AIJobExecutionService async;
    public QuizGenerationController(QuizGenerationService service,com.studyhub.ai.job.AIJobExecutionService async) {this.service=service;this.async=async;}
    @ApiResponse(responseCode="202",description="Accepted; Location points to job status")
    @PostMapping("/api/documents/{documentId}/ai/generate-quiz")
    @Operation(summary="Generate a sourced AI Quiz draft from a Document",description="Bounded published chunk window; saves DRAFT, never publishes. 202 + jobId; same-key replay returns the same job.")
    public ResponseEntity<com.studyhub.ai.job.AIJobAcceptedResponse> document(@PathVariable Long documentId,@RequestHeader("Idempotency-Key") String key,
        @RequestBody GenerateDocumentQuizRequest request,@AuthenticationPrincipal CustomUserDetails user) {
        return async.submitDocumentQuiz(documentId,user.getId(),key,request).accepted();
    }
    @ApiResponse(responseCode="202",description="Accepted; Location points to job status")
    @PostMapping("/api/courses/{courseId}/ai/generate-quiz")
    @Operation(summary="Generate a sourced draft from selected Course Documents",description="1–5 distinct Documents, sorted round-robin window of at most 20 chunks; coverage is explicit and may be partial. 202 + jobId; same-key replay returns the same job.")
    public ResponseEntity<com.studyhub.ai.job.AIJobAcceptedResponse> course(@PathVariable Long courseId,@RequestHeader("Idempotency-Key") String key,
        @RequestBody GenerateCourseQuizRequest request,@AuthenticationPrincipal CustomUserDetails user) {
        return async.submitCourseQuiz(courseId,user.getId(),key,request).accepted();
    }
    @GetMapping("/api/quizzes/{quizId}/ai-generation")
    @Operation(summary="Read saved generation metadata and evidence",description="Does not call AI. sourceCurrent=false after source deletion or re-ingest. Question evidence describes original generation, not subsequent manual edits.")
    public GenerationMetadata metadata(@PathVariable Long quizId,@AuthenticationPrincipal CustomUserDetails user) {return service.getGeneration(quizId,user.getId());}
}
