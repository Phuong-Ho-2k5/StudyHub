package com.studyhub.study;

import com.studyhub.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studyhub.security.CustomUserDetails;
import com.studyhub.study.dto.StartStudySessionRequest;
import com.studyhub.study.dto.StudySessionResponse;
import jakarta.validation.Valid;

@Tag(name = "StudySession", description = "Theo dõi thời gian học của người dùng đăng nhập.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT, token hết hạn hoặc thông tin đăng nhập không đúng.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "Lỗi server; không trả chi tiết nội bộ.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
})
@RestController
@RequestMapping("/api/study-sessions")
public class StudySessionController {
    private final StudySessionService service;

    public StudySessionController(StudySessionService service) {
        this.service = service;
    }

    @PostMapping("/start")
    @Operation(summary = "Bắt đầu phiên học", description = "Kiểm tra quyền Course; mỗi lần gọi tạo một phiên mới. endTime và duration là null khi đang học.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<StudySessionResponse> start(
            @Valid @RequestBody StartStudySessionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.start(request, currentUser.getId()));
    }

    @PostMapping("/{id}/finish")
    @Operation(summary = "Kết thúc phiên học", description = "Không cần body. duration là số giây hoàn chỉnh; chỉ kết thúc được một lần, phiên đã kết thúc trả 409.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Xung đột: email đã tồn tại, phiên đã kết thúc hoặc admin tự hạ quyền.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StudySessionResponse finish(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return service.finish(id, currentUser.getId());
    }
}
