package com.studyhub.search;

import com.studyhub.error.ApiErrorResponse;
import com.studyhub.search.dto.*;
import com.studyhub.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
@Tag(name="Course Search")
@SecurityRequirement(name="bearerAuth")
public class CourseSearchController {
    private final CourseSearchService search;
    public CourseSearchController(CourseSearchService search){this.search=search;}

    @PostMapping("/{courseId}/search")
    @Operation(summary="Tìm đoạn tài liệu theo ngữ nghĩa trong Course",description="Chỉ chủ sở hữu được tìm kiếm. Chỉ lấy phiên bản INDEXED đã công bố, cùng mô hình và số chiều với câu hỏi. topK mặc định 5, từ 1 đến 20. Điểm bằng 1 trừ khoảng cách cosine, không phải xác suất. Đang xử lý lại hoặc thất bại vẫn dùng bản cũ. Không có chỉ mục phù hợp trả items rỗng nếu bước AI thành công.")
    @ApiResponses({
        @ApiResponse(responseCode="200",description="Tối đa topK đoạn, xếp điểm giảm dần; có thể rỗng"),
        @ApiResponse(responseCode="400",description="Câu hỏi hoặc topK không hợp lệ",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="401",description="Thiếu hoặc sai JWT",content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="404",description="Course không tồn tại hoặc không có quyền",content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="422",description="Câu hỏi vượt giới hạn token"),
        @ApiResponse(responseCode="502",description="AI trả vector hoặc cấu hình không hợp lệ"),
        @ApiResponse(responseCode="503",description="AI chưa cấu hình, đang bận hoặc không khả dụng"),
        @ApiResponse(responseCode="504",description="AI quá thời gian chờ")
    })
    public CourseSearchResponse search(@PathVariable Long courseId,@Valid @RequestBody CourseSearchRequest request,
            @AuthenticationPrincipal CustomUserDetails user){
        return search.search(courseId,user.getId(),request);
    }
}
