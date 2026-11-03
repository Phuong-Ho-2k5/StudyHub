package com.studyhub.quiz;

import com.studyhub.quiz.dto.*;
import com.studyhub.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.studyhub.error.ApiErrorResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quizzes/{quizId}")
@Tag(name = "AI Quiz Review", description = "Owner-only review of current AI Quiz content. 404 for resources outside scope; 409 for stale revision or invalid state. Mutations never call AI.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Invalid input or missing AI content revision", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "404", description = "Quiz or question not found in owner scope", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "409", description = "Stale revision, invalid state or incomplete review", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "Internal error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class QuizReviewController {
    private final QuizReviewService service;
    public QuizReviewController(QuizReviewService service) { this.service = service; }

    @GetMapping("/review")
    @Operation(summary = "Read current Quiz review", description = "Current questions, revision, approvals and original generation evidence. sourceCurrent is informational; citations do not certify edits.")
    public QuizReviewResponse get(@PathVariable Long quizId, @AuthenticationPrincipal CustomUserDetails user) {
        return service.get(quizId, user.getId());
    }
    @PostMapping("/questions/{questionId}/review")
    @Operation(summary = "Approve a current question", description = "AI DRAFT only; requires expectedRevision. Repeating the approval preserves its timestamp.")
    public QuizReviewResponse review(@PathVariable Long quizId, @PathVariable Long questionId,
            @RequestBody QuizReviewRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        return service.reviewQuestion(quizId, questionId, request.expectedRevision(), user.getId());
    }
    @PostMapping("/questions/{questionId}/unreview")
    @Operation(summary = "Revoke approval", description = "AI DRAFT or CONFIRMED; increments revision and resets confirmation.")
    public QuizReviewResponse unreview(@PathVariable Long quizId, @PathVariable Long questionId,
            @RequestBody QuizReviewRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        return service.unreviewQuestion(quizId, questionId, request.expectedRevision(), user.getId());
    }
    @PostMapping("/confirm")
    @Operation(summary = "Confirm reviewed Quiz", description = "All current questions must be valid and approved. Confirmation does not publish or permit submission.")
    public QuizReviewResponse confirm(@PathVariable Long quizId, @RequestBody QuizReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return service.confirm(quizId, request.expectedRevision(), user.getId());
    }
    @PostMapping("/publish")
    @Operation(summary = "Publish confirmed Quiz", description = "Separate explicit action after CONFIRMED. Repeating with the same revision preserves publishedAt.")
    public QuizReviewResponse publish(@PathVariable Long quizId, @RequestBody QuizReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return service.publish(quizId, request.expectedRevision(), user.getId());
    }
    @PostMapping("/archive")
    @Operation(summary = "Archive AI Quiz", description = "Terminal state in v1; requires current revision.")
    public QuizReviewResponse archive(@PathVariable Long quizId, @RequestBody QuizReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return service.archive(quizId, request.expectedRevision(), user.getId());
    }
}
