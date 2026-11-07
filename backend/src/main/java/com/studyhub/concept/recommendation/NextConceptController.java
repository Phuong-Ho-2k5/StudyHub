package com.studyhub.concept.recommendation;

import com.studyhub.error.ApiErrorResponse;
import com.studyhub.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/recommendations")
@Tag(name="Concept Recommendations")
@SecurityRequirement(name="bearerAuth")
public class NextConceptController {
    private final NextConceptService service;
    public NextConceptController(NextConceptService service) {this.service=service;}

    @GetMapping("/next-concepts")
    @Operation(summary="Đề xuất Concept nên học tiếp",description="Owner-only. Confidence >=80 đủ prerequisite; kiểm tra cả prerequisite gián tiếp. Ưu tiên nhóm <50, số hậu duệ chưa thành thạo giảm dần, confidence tăng dần rồi ID. unlocksCount không có nghĩa mở khóa ngay. Quiz/StudySession là evidence cấp Course. Không gọi AI hoặc sửa dữ liệu. Giới hạn graph 2000 Concept/10000 cạnh; nhánh cycle hoặc cạnh khác Course bị chặn.")
    @ApiResponses({
        @ApiResponse(responseCode="200",description="READY, NO_CONCEPTS, ALL_MASTERED hoặc BLOCKED_BY_GRAPH"),
        @ApiResponse(responseCode="400",description="limit phải là số nguyên 1–10",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="401",description="JWT thiếu/sai",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="404",description="Course không tồn tại hoặc ngoài scope",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="422",description="Graph vượt giới hạn",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))})
    public NextConceptModels.NextConceptResponse recommend(@PathVariable long courseId,
            @RequestParam(defaultValue="5") @Min(1) @Max(10) int limit,
            @AuthenticationPrincipal CustomUserDetails user) {
        return service.recommend(courseId,user.getId(),limit);
    }
}
