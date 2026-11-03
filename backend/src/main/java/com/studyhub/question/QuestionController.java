package com.studyhub.question;

import com.studyhub.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;

import com.studyhub.question.dto.QuestionRequest;
import com.studyhub.question.dto.QuestionResponse;
import com.studyhub.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Question", description = "API soạn câu hỏi của owner Quiz; response chứa đáp án đúng và đáp án mẫu, không dùng làm đề thi cho người học.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT, token hết hạn hoặc thông tin đăng nhập không đúng.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "Lỗi server; không trả chi tiết nội bộ.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
})
@RestController
@RequestMapping("/api")
public class QuestionController {
    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/quizzes/{quizId}/questions")
    @Operation(summary = "Tạo câu hỏi", description = "MANUAL: MULTIPLE_CHOICE có ít nhất 4 lựa chọn và ít nhất 1 đúng; TRUE_FALSE có 2 lựa chọn và đúng 1 đúng; SHORT_ANSWER cần referenceAnswer và options rỗng. AI: chỉ MCQ với đúng 4 option khác nhau, đúng 1 đáp án đúng, referenceAnswer null; bắt buộc expectedRevision từ GET review, chỉ DRAFT/CONFIRMED. Tạo câu tăng revision, quay DRAFT và xóa confirmation. Concept phải cùng Course.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Thành công"),
        @ApiResponse(responseCode = "409", description = "AI Quiz revision is stale or its state forbids editing", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<QuestionResponse> create(@PathVariable Long quizId,
            @Valid @RequestBody QuestionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(questionService.create(quizId, request, currentUser.getId()));
    }

    @GetMapping("/quizzes/{quizId}/questions")
    @Operation(summary = "Danh sách câu hỏi của Quiz", description = "Chỉ owner được truy cập; response có đáp án đúng và đáp án mẫu.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<QuestionResponse>> list(@PathVariable Long quizId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(questionService.list(quizId, currentUser.getId()));
    }

    @GetMapping("/questions/{id}")
    @Operation(summary = "Chi tiết câu hỏi", description = "Chỉ owner được truy cập; response có đáp án đúng và đáp án mẫu.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<QuestionResponse> get(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(questionService.get(id, currentUser.getId()));
    }

    @PutMapping("/questions/{id}")
    @Operation(summary = "Cập nhật câu hỏi", description = "Thay toàn bộ nội dung/options theo quy tắc tạo câu hỏi; không tính lại điểm Attempt đã lưu. AI chỉ nhận MCQ đúng 4 option khác nhau, đúng 1 đáp án đúng, referenceAnswer null và expectedRevision hiện tại. Chỉ DRAFT/CONFIRMED được sửa; sửa hủy approval câu này, tăng revision và quay DRAFT. PUBLISHED/ARCHIVED khóa sửa. MANUAL giữ quy tắc hiện có.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "409", description = "AI Quiz revision is stale or its state forbids editing", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<QuestionResponse> update(@PathVariable Long id,
            @Valid @RequestBody QuestionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(questionService.update(id, request, currentUser.getId()));
    }

    @DeleteMapping("/questions/{id}")
    @Operation(summary = "Xóa câu hỏi", description = "Owner-only; ngoài scope trả 404. AI cần query expectedRevision, chỉ DRAFT/CONFIRMED; xóa tăng revision, quay DRAFT và hủy confirmation. MANUAL không bắt buộc revision.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Thành công", content = @Content),
        @ApiResponse(responseCode = "409", description = "AI Quiz revision is stale or its state forbids editing", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Missing or invalid AI content revision", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id,
            @io.swagger.v3.oas.annotations.Parameter(description = "Required for AI Quiz: revision from GET review. AI PUBLISHED/ARCHIVED cannot be edited.")
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long expectedRevision,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        questionService.delete(id, currentUser.getId(), expectedRevision);
        return ResponseEntity.noContent().build();
    }
}
