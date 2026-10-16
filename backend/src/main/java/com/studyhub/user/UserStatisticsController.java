package com.studyhub.user;

import com.studyhub.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.studyhub.security.CustomUserDetails;
import com.studyhub.user.dto.UserStatisticsResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "UserStatistics", description = "Thống kê của người dùng đăng nhập trên tất cả Course.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT, token hết hạn hoặc thông tin đăng nhập không đúng.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "Lỗi server; không trả chi tiết nội bộ.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
})
@RestController
@RequestMapping("/api/users/me")
public class UserStatisticsController {
    private final UserStatisticsService service;

    public UserStatisticsController(UserStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/statistics")
    @Operation(summary = "Thống kê học tập cá nhân", description = "Tổng số phiên đã kết thúc, thời lượng tính bằng giây, số lần làm Quiz và trung bình điểm Attempt đã lưu (0–100), làm tròn 2 chữ số. Chưa có dữ liệu trả 0.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công")
    })
    public UserStatisticsResponse getStatistics(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return service.getStatistics(currentUser.getId());
    }
}
