package com.studyhub.workspace;

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

import com.studyhub.security.CustomUserDetails;
import com.studyhub.workspace.dto.CreateWorkspaceRequest;
import com.studyhub.workspace.dto.UpdateWorkspaceRequest;
import com.studyhub.workspace.dto.WorkspaceResponse;

import jakarta.validation.Valid;

@Tag(name = "Workspace", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT, token hết hạn hoặc thông tin đăng nhập không đúng.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "Lỗi server; không trả chi tiết nội bộ.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
})
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    @Operation(summary = "Tạo Workspace", description = "Owner được lấy từ JWT, không nhận ownerId từ body.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            @Valid @RequestBody CreateWorkspaceRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        WorkspaceResponse response = workspaceService.createWorkspace(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Danh sách Workspace", description = "Trả danh sách Workspace của người dùng đăng nhập; chưa có dữ liệu trả mảng rỗng.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công")
    })
    public List<WorkspaceResponse> getAllWorkspaces(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return workspaceService.getAllWorkspaces(currentUser.getId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết Workspace", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public WorkspaceResponse getWorkspaceById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return workspaceService.getWorkspace(id, currentUser.getId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật Workspace", description = "Thay tên và mô tả. Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public WorkspaceResponse updateWorkspace(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWorkspaceRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return workspaceService.updateWorkspace(id, request, currentUser.getId());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa Workspace", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Thành công", content = @Content),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteWorkspace(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        workspaceService.deleteWorkspace(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
