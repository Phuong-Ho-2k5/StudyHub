package com.studyhub.concept;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.studyhub.security.CustomUserDetails;
import com.studyhub.concept.dto.CreateConceptRequest;
import com.studyhub.concept.dto.UpdateConceptRequest;
import com.studyhub.concept.dto.AddPrerequisiteRequest;
import com.studyhub.concept.dto.ConceptResponse;

import com.studyhub.concept.ConceptService;
import java.util.List;
import jakarta.validation.Valid;

@Tag(name = "Concept", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT, token hết hạn hoặc thông tin đăng nhập không đúng.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "500", description = "Lỗi server; không trả chi tiết nội bộ.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
})
@RestController
@RequestMapping("/api")
public class ConceptController {
    private final ConceptService conceptService;

    public ConceptController(ConceptService conceptService) {
        this.conceptService = conceptService;
    }

    @GetMapping("/concepts/{id}")
    @Operation(summary = "Chi tiết Concept", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ConceptResponse> getConcept(
        @PathVariable Long id,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        ConceptResponse concept = conceptService.getConcept(id, currentUser.getId());
        return ResponseEntity.ok(concept);
    }

    @GetMapping("/concepts")
    @Operation(summary = "Danh sách Concept trong Course", description = "courseId là bắt buộc. Trả mảng rỗng nếu Course chưa có Concept. Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<ConceptResponse>> getConceptFromCourse(
        @RequestParam Long courseId,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<ConceptResponse> concepts = conceptService.getConceptFromCourse(courseId, currentUser.getId());
        return ResponseEntity.ok(concepts);
    }

    @GetMapping("/courses/{courseId}/concepts")
    @Operation(summary = "Lọc Concept cần ôn tập", description = "confidenceBelow bắt buộc trong khoảng 0–100; lấy Concept có confidence nhỏ hơn ngưỡng. Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<ConceptResponse>> getConceptsWithLowConfidence(
        @PathVariable Long courseId,
        @RequestParam @Min(0) @Max(100) Integer confidenceBelow,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<ConceptResponse> concepts = conceptService.getConceptsWithLowConfidence(courseId, confidenceBelow, currentUser.getId());
        return ResponseEntity.ok(concepts);
    }

    @GetMapping("/concepts/{id}/prerequisites")
    @Operation(summary = "Danh sách kiến thức tiên quyết", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<ConceptResponse>> getPrerequisites(
        @PathVariable Long id,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<ConceptResponse> prerequisites = conceptService.getPrerequisites(id, currentUser.getId());
        return ResponseEntity.ok(prerequisites);
    }

    @PostMapping("/courses/{courseId}/concepts")
    @Operation(summary = "Tạo Concept", description = "confidence trong khoảng 0–100; status bắt buộc. Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ConceptResponse> createConcept(
        @PathVariable Long courseId,
        @Valid @RequestBody CreateConceptRequest request,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        ConceptResponse response = conceptService.createConcept(courseId, request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/concepts/{id}/prerequisites")
    @Operation(summary = "Thêm kiến thức tiên quyết", description = "Hai Concept phải cùng Course; không tự tham chiếu và không thêm trùng quan hệ.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Thành công", content = @Content),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> addPrerequisite(
        @PathVariable Long id,
        @Valid @RequestBody AddPrerequisiteRequest request,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        conceptService.addPrerequisite(id, request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/concepts/{id}")
    @Operation(summary = "Cập nhật Concept", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thành công"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu, tham số hoặc quy tắc nghiệp vụ không hợp lệ; lỗi validation có fieldErrors.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ConceptResponse> updateConcept(
        @PathVariable Long id,
        @Valid @RequestBody UpdateConceptRequest request,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        ConceptResponse response = conceptService.updateConcept(id, request, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/concepts/{id}")
    @Operation(summary = "Xóa Concept", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Thành công", content = @Content),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteConcept(
        @PathVariable Long id,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        conceptService.deleteConcept(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/concepts/{id}/prerequisites/{prerequisiteId}")
    @Operation(summary = "Gỡ kiến thức tiên quyết", description = "Chỉ thao tác tài nguyên thuộc người dùng đăng nhập qua owner Workspace. Không tồn tại hoặc không có quyền trả 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Thành công", content = @Content),
        @ApiResponse(responseCode = "404", description = "Tài nguyên không tồn tại hoặc người dùng không có quyền truy cập.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> removePrerequisite(
        @PathVariable Long id,
        @PathVariable Long prerequisiteId,
        @AuthenticationPrincipal CustomUserDetails currentUser) {
        conceptService.removePrerequisite(id, prerequisiteId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
