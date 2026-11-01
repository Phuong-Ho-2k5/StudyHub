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
    @Operation(summary="Hỏi đáp dựa trên tài liệu của Course", description="Chỉ owner được hỏi. sessionId tùy chọn: có ID thì lưu nguyên tử question/answer/citation vào session cùng User + Course, trả sessionId/userMessageId/assistantMessageId; bỏ qua hoặc null thì không lưu và các ID trả null. Mỗi câu hỏi độc lập, không gửi history vào prompt. Dùng phiên bản đã công bố. ANSWERED có citation với quote nguyên văn từ context thực sự gửi LLM; INSUFFICIENT_CONTEXT có citations rỗng và vẫn được lưu nếu có session. Lỗi AI/citation không thêm message; lỗi lưu không trả thành công. Context được gửi đến API LLM bên ngoài. Citation kiểm chứng provenance, không bảo đảm mọi khẳng định đều đúng ngữ nghĩa.")
    @ApiResponses({
        @ApiResponse(responseCode="200", description="ANSWERED hoặc INSUFFICIENT_CONTEXT"),
        @ApiResponse(responseCode="400", description="Question, topK hoặc sessionId không hợp lệ", content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="401", description="Thiếu hoặc sai JWT", content=@Content(mediaType="application/json", schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="404", description="Course/session không tồn tại hoặc không thuộc đúng User + Course", content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="422", description="Vượt token budget"),
        @ApiResponse(responseCode="409", description="Nguồn citation thay đổi trong lúc tạo câu trả lời; hỏi lại",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="502", description="AI trả dữ liệu không hợp lệ"),
        @ApiResponse(responseCode="503", description="AI chưa cấu hình hoặc không khả dụng"),
        @ApiResponse(responseCode="504", description="AI quá thời gian chờ")
    })
    public CourseChatResponse chat(@PathVariable Long courseId, @Valid @RequestBody CourseChatRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return chat.chat(courseId, user.getId(), request);
    }
}
