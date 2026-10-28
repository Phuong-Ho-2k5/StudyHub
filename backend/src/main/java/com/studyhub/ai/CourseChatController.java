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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
@Tag(name="Course Chat")
@SecurityRequirement(name="bearerAuth")
public class CourseChatController {
    private final CourseChatService chat;
    public CourseChatController(CourseChatService chat) { this.chat = chat; }

    @PostMapping("/{courseId}/ai/chat")
    @Operation(summary="Hỏi đáp dựa trên tài liệu của Course", description="Chỉ owner được hỏi. Dùng phiên bản đã công bố, không lưu lịch sử. Thiếu bằng chứng trả INSUFFICIENT_CONTEXT. Context được gửi đến API LLM bên ngoài; citation kiểm chứng chưa thuộc endpoint này.")
    @ApiResponses({
        @ApiResponse(responseCode="200", description="ANSWERED hoặc INSUFFICIENT_CONTEXT"),
        @ApiResponse(responseCode="400", description="Question hoặc topK không hợp lệ", content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="401", description="Thiếu hoặc sai JWT", content=@Content(mediaType="application/json", schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="404", description="Course không tồn tại hoặc không có quyền", content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="422", description="Vượt token budget"),
        @ApiResponse(responseCode="502", description="AI trả dữ liệu không hợp lệ"),
        @ApiResponse(responseCode="503", description="AI chưa cấu hình hoặc không khả dụng"),
        @ApiResponse(responseCode="504", description="AI quá thời gian chờ")
    })
    public CourseChatResponse chat(@PathVariable Long courseId, @Valid @RequestBody CourseChatRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return chat.chat(courseId, user.getId(), request);
    }
}
