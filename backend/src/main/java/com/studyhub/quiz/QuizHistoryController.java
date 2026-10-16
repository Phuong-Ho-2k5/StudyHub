package com.studyhub.quiz;

import com.studyhub.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studyhub.quiz.dto.QuizHistoryResponse;
import com.studyhub.security.CustomUserDetails;

@Tag(name = "QuizHistory", description = "Lịch sử kết quả đã lưu của user trong JWT; không nhận userId.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT, token hết hạn hoặc thông tin đăng nhập không đúng.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "Lỗi server; không trả chi tiết nội bộ.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
})
@RestController
@RequestMapping("/api/users/me")
public class QuizHistoryController {
    private final QuizHistoryService historyService;

    public QuizHistoryController(QuizHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/quiz-history")
    @Operation(summary = "Lịch sử làm Quiz có phân trang", description = "Mặc định page=0, size=10, createdAt DESC rồi id DESC. Mỗi lần nộp là một bản ghi; không tính lại điểm khi câu hỏi thay đổi. Trang vượt cuối trả content rỗng, giữ tổng bản ghi.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Page<QuizHistoryResponse> getHistory(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PageableDefault(size = 10, sort = {"createdAt", "id"},
                    direction = Sort.Direction.DESC) @ParameterObject Pageable pageable) {
        return historyService.getHistory(currentUser.getId(), pageable);
    }
}
