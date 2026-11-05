package com.studyhub.ai.advice;

import com.studyhub.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
@Tag(name="Learning Advice")
@SecurityRequirement(name="bearerAuth")
public class LearningAdviceController {
    private final LearningAdviceService service;
    public LearningAdviceController(LearningAdviceService service) {this.service=service;}

    @PostMapping("/{courseId}/ai/learning-advice")
    @Operation(summary="Tư vấn học tập cá nhân hóa theo Course",description="Chỉ dùng Progress, confidence, QuizAttempt và phiên học hoàn tất của User/Course hiện tại. RAG chỉ đọc nguồn đã công bố. Trả learning facts và citation đã kiểm tra; thiếu dữ liệu trả INSUFFICIENT_CONTEXT, thiếu nguồn có thể trả LEARNING_DATA_ONLY. Snapshot có asOf. Không lưu advice hay thay đổi dữ liệu học. Learning context và tài liệu được gửi tới dịch vụ LLM bên ngoài; evidence kiểm tra provenance, không bảo đảm đúng ngữ nghĩa.")
    @ApiResponses({
        @ApiResponse(responseCode="200",description="ANSWERED, LEARNING_DATA_ONLY hoặc INSUFFICIENT_CONTEXT"),
        @ApiResponse(responseCode="400",description="Input không hợp lệ hoặc field lạ"),
        @ApiResponse(responseCode="401",description="JWT thiếu/sai"),
        @ApiResponse(responseCode="404",description="Course không tồn tại hoặc ngoài scope"),
        @ApiResponse(responseCode="409",description="Nguồn citation thay đổi trong lúc sinh"),
        @ApiResponse(responseCode="422",description="Vượt token budget"),
        @ApiResponse(responseCode="502",description="Output AI hoặc evidence không hợp lệ"),
        @ApiResponse(responseCode="503",description="Dependency AI chưa cấu hình hoặc unavailable"),
        @ApiResponse(responseCode="504",description="Dependency AI timeout")})
    public LearningAdviceModels.AdviceResponse advise(@PathVariable Long courseId,@RequestBody AdviceRequest request,
                                                      @AuthenticationPrincipal CustomUserDetails user) {
        return service.advise(courseId,user.getId(),request);
    }
}
