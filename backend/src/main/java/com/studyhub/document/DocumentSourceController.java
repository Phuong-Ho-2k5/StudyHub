package com.studyhub.document;

import com.studyhub.document.dto.DocumentSourceResponse;
import com.studyhub.security.CustomUserDetails;
import com.studyhub.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/documents/{documentId}/chunks")
@Tag(name="Document Sources")
@SecurityRequirement(name="bearerAuth")
public class DocumentSourceController {
    private final DocumentSourceService sources;
    public DocumentSourceController(DocumentSourceService sources) { this.sources=sources; }
    @GetMapping("/{chunkId}/source")
    @Operation(summary="Mở đoạn nguồn citation",description="Chỉ owner Course. Trả chunk của bản published INDEXED; citation cũ không được đổi sang chunk mới. Trang không có provenance trả null.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Đoạn nguồn hiện hành"),
        @ApiResponse(responseCode="401",description="Thiếu hoặc sai JWT",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="404",description="Course/Document không tồn tại, không có quyền hoặc không khớp",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="409",description="Chunk không còn thuộc bản published INDEXED",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))})
    public DocumentSourceResponse get(@PathVariable Long courseId,@PathVariable Long documentId,@PathVariable Long chunkId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return sources.getSource(courseId,documentId,chunkId,user.getId());
    }
}
