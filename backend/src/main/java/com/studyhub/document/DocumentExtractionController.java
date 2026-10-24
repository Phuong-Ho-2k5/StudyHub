package com.studyhub.document;

import com.studyhub.document.dto.DocumentExtractionResponse;
import com.studyhub.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Document")
@SecurityRequirement(name = "bearerAuth")
public class DocumentExtractionController {
    private final DocumentService documents;
    private final DocumentExtractionClient ai;

    public DocumentExtractionController(DocumentService documents, DocumentExtractionClient ai) {
        this.documents = documents;
        this.ai = ai;
    }

    @PostMapping("/{documentId}/extract")
    @Operation(summary = "Trích xuất PDF bằng Docling",
        description = "Kiểm tra owner rồi đọc PDF đã upload. Trả text, bảng và hình theo trang, vị trí và cảnh báo. "
            + "OCR tiếng Việt/Anh; mô tả hình do mô hình sinh được đánh dấu riêng nếu đã bật. "
            + "Xử lý đồng bộ; không lưu chunk/vector và không đổi trạng thái Document.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Đã trích xuất nội dung theo trang"),
        @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT"),
        @ApiResponse(responseCode = "404", description = "Document không tồn tại hoặc không thuộc owner"),
        @ApiResponse(responseCode = "409", description = "Metadata cũ hoặc tệp không còn khả dụng"),
        @ApiResponse(responseCode = "413", description = "Vượt giới hạn dung lượng/số trang"),
        @ApiResponse(responseCode = "415", description = "PDF không hợp lệ hoặc có mật khẩu"),
        @ApiResponse(responseCode = "422", description = "Không trích xuất được đầy đủ tài liệu"),
        @ApiResponse(responseCode = "502", description = "AI Service trả lỗi hoặc dữ liệu không hợp lệ"),
        @ApiResponse(responseCode = "503", description = "AI Service bận, thiếu cấu hình hoặc không sẵn sàng"),
        @ApiResponse(responseCode = "504", description = "Trích xuất quá thời gian cho phép")
    })
    public DocumentExtractionResponse extract(@PathVariable Long documentId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        // The short database transaction ends before model/network work begins.
        var source = documents.readForExtraction(documentId, currentUser.getId());
        return ai.extract(source.id(), source.fileName(), source.bytes());
    }
}
