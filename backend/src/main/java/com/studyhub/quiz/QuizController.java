package com.studyhub.quiz;

import com.studyhub.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;

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

import com.studyhub.quiz.dto.CreateQuizRequest;
import com.studyhub.quiz.dto.QuizResponse;
import com.studyhub.quiz.dto.SubmitQuizRequest;
import com.studyhub.quiz.dto.UpdateQuizRequest;
import com.studyhub.quiz.dto.QuizResult;
import com.studyhub.security.CustomUserDetails;

import jakarta.validation.Valid;

@Tag(name = "Quiz", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT, token hết hạn hoặc thông tin đăng nhập không đúng.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "Lỗi server; không trả chi tiết nội bộ.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
})
@RestController
@RequestMapping("/api")
public class QuizController {
    private final QuizService quizService;
    private final QuizSubmissionService quizSubmissionService;

    public QuizController(QuizService quizService, QuizSubmissionService quizSubmissionService) {
        this.quizService = quizService;
        this.quizSubmissionService = quizSubmissionService;
    }

    @PostMapping("/courses/{courseId}/quizzes")
    @Operation(summary = "Tạo Quiz", description = "Tạo Quiz thủ công với sourceType=MANUAL và trạng thái DRAFT. Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<QuizResponse> create(@PathVariable Long courseId,
            @Valid @RequestBody CreateQuizRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(quizService.create(courseId, request, currentUser.getId()));
    }

    @GetMapping("/courses/{courseId}/quizzes")
    @Operation(summary = "Danh sách Quiz trong Course", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<QuizResponse>> list(@PathVariable Long courseId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(quizService.list(courseId, currentUser.getId()));
    }

    @GetMapping("/quizzes/{id}")
    @Operation(summary = "Chi tiết Quiz", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<QuizResponse> get(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(quizService.get(id, currentUser.getId()));
    }

    @PutMapping("/quizzes/{id}")
    @Operation(summary = "Cập nhật Quiz", description = "Thay title và status DRAFT, PUBLISHED hoặc ARCHIVED. Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<QuizResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdateQuizRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(quizService.update(id, request, currentUser.getId()));
    }

    @DeleteMapping("/quizzes/{id}")
    @Operation(summary = "Xóa Quiz", description = "Các lần nộp liên quan bị xóa theo Quiz. Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Thành công", content = @Content),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        quizService.delete(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/quizzes/{quizId}/submit")
    @Operation(summary = "Nộp và chấm Quiz", description = "Chỉ nhận Quiz PUBLISHED. Mỗi câu phải được trả lời đúng một lần. Trắc nghiệm dùng answerOptionIds, SHORT_ANSWER dùng answerText và so khớp nguyên chuỗi với đáp án mẫu, không gọi AI. Mỗi lần nộp lưu Attempt mới, cập nhật confidence và tạo thông báo trong cùng transaction.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<QuizResult> submit(@PathVariable Long quizId,
            @Valid @RequestBody SubmitQuizRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        QuizResult result = quizSubmissionService.submitQuiz(quizId, request, currentUser.getId());
        return ResponseEntity.ok(result);
    }
}
