package com.studyhub.ai;

import com.studyhub.ai.dto.*;
import com.studyhub.error.ApiErrorResponse;
import com.studyhub.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/courses/{courseId}/ai/chat-sessions")
@Tag(name="Chat History")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode="401",description="Missing or invalid JWT",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
    @ApiResponse(responseCode="404",description="Course or session not found in the current user's scope",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
    @ApiResponse(responseCode="400",description="Invalid title or paging (page * size must not exceed 2147483647); sort is fixed and cannot be overridden",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
})
public class ChatSessionController {
    private final ChatHistoryService history;
    public ChatSessionController(ChatHistoryService history) { this.history=history; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Create a conversation in the current user's Course",description="Title is trimmed, 1–150 characters. Creates an empty session; does not call AI.")
    @ApiResponse(responseCode="201",description="Created")
    public ChatSessionResponse create(@PathVariable Long courseId, @Valid @RequestBody CreateChatSessionRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return history.createSession(courseId,user.getId(),request);
    }

    @GetMapping
    @Operation(summary="List conversations in a Course",description="Fixed order updatedAt DESC, id DESC. page >= 0; size 1–100, default 10. Out-of-range pages return empty content.")
    @ApiResponse(responseCode="200",description="Page of session metadata")
    public Page<ChatSessionResponse> list(@PathVariable Long courseId, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size, @RequestParam(required=false) String sort,
            @AuthenticationPrincipal CustomUserDetails user) {
        return history.listSessions(courseId,user.getId(),paging(page,size,sort,Sort.by(Sort.Direction.DESC,"updatedAt","id")));
    }

    @GetMapping("/{sessionId}/messages")
    @Operation(summary="Read saved conversation messages",description="Fixed order id ASC, page >= 0; size 1–100, default 20. Citations are saved snapshots; source links still require current access and may expire. Reading history does not call AI.")
    @ApiResponse(responseCode="200",description="Page of saved messages and citation snapshots")
    public Page<ChatMessageResponse> messages(@PathVariable Long courseId, @PathVariable Long sessionId,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String sort, @AuthenticationPrincipal CustomUserDetails user) {
        return history.listMessages(courseId,user.getId(),sessionId,paging(page,size,sort,Sort.by("id")));
    }

    private Pageable paging(int page, int size, String requestedSort, Sort fixedSort) {
        if(page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE || requestedSort!=null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"page must be >= 0, size 1–100, page * size <= 2147483647; sort cannot be overridden");
        return PageRequest.of(page,size,fixedSort);
    }
}
