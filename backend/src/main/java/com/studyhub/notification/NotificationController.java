package com.studyhub.notification;

import com.studyhub.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springdoc.core.annotations.ParameterObject;
import com.studyhub.notification.dto.NotificationResponse;
import com.studyhub.notification.dto.UnreadCountResponse;
import com.studyhub.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "Chỉ truy cập thông báo của user trong JWT; không nhận userId để chọn người nhận.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT, token hết hạn hoặc thông tin đăng nhập không đúng.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "Lỗi server; không trả chi tiết nội bộ.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
})
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Danh sách thông báo có phân trang", description = "Mặc định page=0, size=20, createdAt DESC rồi id DESC. Không có dữ liệu trả trang rỗng.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Page<NotificationResponse> list(@AuthenticationPrincipal CustomUserDetails user,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) @ParameterObject Pageable pageable) {
        return service.list(user.getId(), pageable);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Đếm thông báo chưa đọc", description = "Trả unreadCount của người dùng đăng nhập; chưa có thông báo trả 0.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công")
    })
    public UnreadCountResponse countUnread(@AuthenticationPrincipal CustomUserDetails user) {
        return new UnreadCountResponse(service.countUnread(user.getId()));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Đánh dấu thông báo đã đọc", description = "Không cần body; gọi lại vẫn trả 200. Thông báo không tồn tại hoặc thuộc người khác trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public NotificationResponse markAsRead(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        return service.markAsRead(id, user.getId());
    }
}
