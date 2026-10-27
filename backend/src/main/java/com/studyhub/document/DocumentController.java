package com.studyhub.document;

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

import com.studyhub.document.dto.CreateDocumentRequest;
import com.studyhub.document.dto.UpdateDocumentRequest;
import com.studyhub.document.dto.DocumentResponse;
import com.studyhub.security.CustomUserDetails;

import org.springframework.data.domain.Pageable;

import com.studyhub.document.DocumentService;
import java.util.List;
import org.springframework.data.domain.Page;

import jakarta.validation.Valid;

@Tag(name = "Document", description = "Quản lý tài liệu và tệp PDF theo owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT, token hết hạn hoặc thông tin đăng nhập không đúng.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "Lỗi server; không trả chi tiết nội bộ.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
})
@RestController
@RequestMapping("/api")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/courses/{courseId}/documents/upload", consumes = "multipart/form-data")
    @Operation(summary = "Tải tài liệu PDF lên Course", description = "Nhận title và file PDF. Kiểm tra quyền Course, nội dung PDF và dung lượng cấu hình. Tên lưu do server tạo. Tạo tác vụ ingestion PENDING trong cùng giao dịch; worker xử lý sau khi upload commit. Chỉ tìm kiếm khi có publishedIngestionId.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Đã lưu tệp và metadata"),
        @ApiResponse(responseCode = "400", description = "Thiếu tệp hoặc tiêu đề không hợp lệ"),
        @ApiResponse(responseCode = "404", description = "Course không tồn tại hoặc không có quyền"),
        @ApiResponse(responseCode = "413", description = "Tệp vượt giới hạn dung lượng"),
        @ApiResponse(responseCode = "415", description = "Tệp không phải PDF hợp lệ")
    })
    public ResponseEntity<DocumentResponse> upload(@PathVariable Long courseId, @RequestParam String title,
            @RequestParam org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.upload(courseId, title, file, currentUser.getId()));
    }

    @PostMapping("/courses/{courseId}/documents")
    @Operation(summary = "Tạo metadata tài liệu", description = "Lưu title, fileName, fileType, storagePath; không tải tệp. Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<DocumentResponse> createDocument(
            @PathVariable Long courseId,
            @Valid @RequestBody CreateDocumentRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        DocumentResponse response = documentService.createDocument(courseId, request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/documents/{documentId}")
    @Operation(summary = "Cập nhật metadata tài liệu", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long documentId,
            @Valid @RequestBody UpdateDocumentRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        DocumentResponse response = documentService.updateDocument(documentId, request, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/documents/{documentId}")
    @Operation(summary = "Xóa metadata tài liệu", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Thành công", content = @Content),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long documentId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        documentService.deleteDocument(documentId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documents/{documentId}")
    @Operation(summary = "Chi tiết metadata tài liệu", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable Long documentId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        DocumentResponse response = documentService.getDocument(documentId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/documents")
    @Operation(summary = "Danh sách tài liệu có phân trang", description = "Lọc theo workspaceId, courseId, status và q. Chỉ trả tài liệu thuộc người dùng đăng nhập. Mặc định page=0, size=20.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Page<DocumentResponse>> getAllDocument(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) String q,
            @ParameterObject Pageable pageable) {
        Page<DocumentResponse> response = documentService.getAllDocument(currentUser.getId(), workspaceId, courseId, status, q, pageable);
        return ResponseEntity.ok(response);
    }
}
