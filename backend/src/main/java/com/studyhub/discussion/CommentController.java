package com.studyhub.discussion;

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
import com.studyhub.discussion.dto.CommentRequest;
import com.studyhub.discussion.dto.CommentResponse;
import com.studyhub.security.CustomUserDetails;
import jakarta.validation.Valid;

@Tag(name = "Comment", description = "Bình luận một cấp theo bài thảo luận. Cần quyền Course qua owner Workspace.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT, token hết hạn hoặc thông tin đăng nhập không đúng.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "Lỗi server; không trả chi tiết nội bộ.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
})
@RestController
@RequestMapping("/api")
public class CommentController {
    private final CommentService service;

    public CommentController(CommentService service) { this.service = service; }

    @PostMapping("/discussion-posts/{postId}/comments")
    @Operation(summary = "Tạo bình luận", description = "content bắt buộc, tối đa 10000 ký tự trước trim hai đầu; tác giả lấy từ JWT, bài lấy từ URL.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CommentResponse> create(@PathVariable Long postId,
            @Valid @RequestBody CommentRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(postId, request, user.getId()));
    }

    @GetMapping("/discussion-posts/{postId}/comments")
    @Operation(summary = "Danh sách bình luận có phân trang", description = "Mặc định page=0, size=20, createdAt ASC rồi id ASC. Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Page<CommentResponse> list(@PathVariable Long postId, @AuthenticationPrincipal CustomUserDetails user,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.ASC)
            @ParameterObject Pageable pageable) {
        return service.list(postId, user.getId(), pageable);
    }

    @GetMapping("/comments/{id}")
    @Operation(summary = "Chi tiết bình luận", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CommentResponse get(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        return service.get(id, user.getId());
    }

    @PutMapping("/comments/{id}")
    @Operation(summary = "Cập nhật bình luận", description = "Cần quyền Course và là tác giả bình luận. Không đổi bài, tác giả hoặc createdAt.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CommentResponse update(@PathVariable Long id, @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return service.update(id, request, user.getId());
    }

    @DeleteMapping("/comments/{id}")
    @Operation(summary = "Xóa bình luận", description = "Cần quyền Course và là tác giả bình luận; không xóa bài hay bình luận khác.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Thành công", content = @Content),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        service.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
