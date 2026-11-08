package com.studyhub.ai.feedback;

import com.studyhub.security.CustomUserDetails;
import com.studyhub.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/ai/messages/{messageId}/feedback")
@Tag(name="AI Feedback") @SecurityRequirement(name="bearerAuth")
public class AIFeedbackController {
    private final AIFeedbackService service;
    public AIFeedbackController(AIFeedbackService service){this.service=service;}
    @PostMapping
    @Operation(summary="Create or replace feedback on your saved assistant answer",description="One feedback per user/message. Full replacement: omitted/blank note clears it. Identical retries preserve timestamps. Concurrent edits are last-write-wins. Does not call AI or reorder sessions.")
    @ApiResponses({
        @ApiResponse(responseCode="201",description="Created",content=@Content(schema=@Schema(implementation=FeedbackResponse.class))),
        @ApiResponse(responseCode="200",description="Updated or identical retry",content=@Content(schema=@Schema(implementation=FeedbackResponse.class))),
        @ApiResponse(responseCode="400",description="Invalid payload or owned USER message",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="401",description="Missing/invalid JWT",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="404",description="Message not in current user's Session and Course",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))})
    public ResponseEntity<FeedbackResponse> save(@PathVariable long messageId,@AuthenticationPrincipal CustomUserDetails user,@Valid @RequestBody FeedbackRequest request) {
        var result=service.save(messageId,user.getId(),request);
        return ResponseEntity.status(result.created()?201:200).body(result.feedback());
    }
}
