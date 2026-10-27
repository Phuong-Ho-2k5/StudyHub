package com.studyhub.document;

import com.studyhub.document.dto.DocumentIngestionResponse;
import com.studyhub.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.studyhub.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
@Tag(name="Document")
@SecurityRequirement(name="bearerAuth")
public class DocumentIngestionController {
    private final DocumentIngestionService ingestion;
    public DocumentIngestionController(DocumentIngestionService ingestion) { this.ingestion = ingestion; }

    @PostMapping("/{documentId}/ingest")
    @Operation(summary="Xử lý hoặc xử lý lại PDF đã tải lên",description="Chỉ chủ sở hữu được gọi. Xử lý đồng bộ và công bố toàn bộ phiên bản mới trong một giao dịch. Giữ phiên bản cũ khi lỗi; trả 409 nếu tác vụ đang được xử lý. Theo dõi ingestionStatus và publishedIngestionId qua API tài liệu; Document.status giữ nguyên.")
    @ApiResponses({
        @ApiResponse(responseCode="200",description="All chunks and embeddings persisted"),
        @ApiResponse(responseCode="401",description="JWT is missing or invalid",
                content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="404",description="Document does not exist or is not owned by the caller",
                content=@Content(mediaType="application/json",schema=@Schema(implementation=ApiErrorResponse.class))),
        @ApiResponse(responseCode="409",description="Ingestion is processing, claim expired, source changed, or uploaded PDF unavailable"),
        @ApiResponse(responseCode="413",description="PDF exceeds extraction limits"),
        @ApiResponse(responseCode="415",description="PDF is unsupported or invalid"),
        @ApiResponse(responseCode="422",description="Document has no ingestible chunks or cannot be extracted completely"),
        @ApiResponse(responseCode="502",description="AI service returned invalid or incompatible results"),
        @ApiResponse(responseCode="503",description="AI service unavailable or not configured"),
        @ApiResponse(responseCode="504",description="AI service timed out")
    })
    public DocumentIngestionResponse ingest(@PathVariable Long documentId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ingestion.ingest(documentId,currentUser.getId());
    }
}
