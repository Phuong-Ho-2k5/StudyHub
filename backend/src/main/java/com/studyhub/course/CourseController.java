package com.studyhub.course;

import com.studyhub.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.studyhub.security.CustomUserDetails;
import com.studyhub.course.dto.CreateCourseRequest;
import com.studyhub.course.dto.UpdateCourseRequest;
import com.studyhub.course.dto.CourseResponse;
import com.studyhub.course.dto.CourseProgressResponse;

import org.springframework.data.domain.Pageable;

import com.studyhub.course.CourseService;
import java.util.List;
import org.springframework.data.domain.Page;

import jakarta.validation.Valid;

@Tag(name = "Course", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT, token hết hạn hoặc thông tin đăng nhập không đúng.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "Lỗi server; không trả chi tiết nội bộ.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
})
@RestController
@RequestMapping("/api")
public class CourseController {
    private final CourseService courseService;
    private final CourseProgressService courseProgressService;

    public CourseController(CourseService courseService, CourseProgressService courseProgressService) {
        this.courseService = courseService;
        this.courseProgressService = courseProgressService;
    }

    @PostMapping("/workspaces/{workspaceId}/courses")
    @Operation(summary = "Tạo Course trong Workspace", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CourseResponse> createCourse(
            @PathVariable Long workspaceId,
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        CourseResponse response = courseService.createCourse(workspaceId, request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/courses/{id}")
    @Operation(summary = "Chi tiết Course", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CourseResponse getCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return courseService.getCourse(id, currentUser.getId());
    }

    @GetMapping("/courses/{courseId}/progress")
    @Operation(summary = "Tiến độ Course", description = "Tính trung bình confidence của mọi Concept. Nắm vững từ 80, đang phát triển từ 50 đến 79, cần ôn dưới 50. Course rỗng trả 0; không thay đổi trạng thái Course.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CourseProgressResponse getProgress(
            @PathVariable Long courseId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return courseProgressService.getProgress(courseId, currentUser.getId());
    }

    @PutMapping("/courses/{id}")
    @Operation(summary = "Cập nhật Course", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CourseResponse updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCourseRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return courseService.updateCourse(id, request, currentUser.getId());
    }

    @DeleteMapping("/courses/{id}")
    @Operation(summary = "Xóa Course", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Thành công", content = @Content),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        courseService.deleteCourse(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/courses")
    @Operation(summary = "Danh sách Course có phân trang", description = "Lọc theo workspaceId, status và q trong tên, không phân biệt hoa/thường. Chỉ trả Course của người dùng đăng nhập. Mặc định page=0, size=20.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Page<CourseResponse> getAllCourses(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) String q,
            @ParameterObject Pageable pageable) {
        return courseService.getAllCourses(currentUser.getId(), workspaceId, status, q, pageable);
    }
}
