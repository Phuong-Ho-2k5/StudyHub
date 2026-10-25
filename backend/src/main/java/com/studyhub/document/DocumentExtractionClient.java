package com.studyhub.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyhub.document.dto.DocumentExtractionResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DocumentExtractionClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;
    private final String token;
    private final Duration timeout;

    public DocumentExtractionClient(@Value("${studyhub.ai.url:http://localhost:8000}") String baseUrl,
            @Value("${studyhub.ai.token:}") String token,
            @Value("${studyhub.ai.timeout-seconds:240}") long timeoutSeconds) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("AI timeout must be positive");
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public DocumentExtractionResponse extract(Long documentId, String source, byte[] pdf) {
        return extract(documentId, source, pdf, false);
    }

    public DocumentExtractionResponse extract(Long documentId, String source, byte[] pdf, boolean includeChunks) {
        if (token.isBlank() || baseUrl.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI Service chưa được cấu hình.");
        String boundary = "StudyHub-" + UUID.randomUUID();
        try {
            var body = new ByteArrayOutputStream();
            if (includeChunks) {
                body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"includeChunks\"\r\n\r\ntrue\r\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"documentId\"\r\n\r\n" + documentId
                    + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document.pdf\"\r\n"
                    + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(pdf);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/internal/documents/extract"))
                    .timeout(timeout).header("X-AI-Service-Token", token)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                HttpStatus status = switch (response.statusCode()) {
                    case 413 -> HttpStatus.PAYLOAD_TOO_LARGE;
                    case 415 -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
                    case 422 -> HttpStatus.UNPROCESSABLE_ENTITY;
                    case 503, 401, 403 -> HttpStatus.SERVICE_UNAVAILABLE;
                    case 504 -> HttpStatus.GATEWAY_TIMEOUT;
                    default -> HttpStatus.BAD_GATEWAY;
                };
                throw new ResponseStatusException(status, "Không thể trích xuất tài liệu.");
            }
            DocumentExtractionResponse result;
            try {
                result = json.readValue(response.body(), DocumentExtractionResponse.class);
                if (result == null || !documentId.equals(result.documentId()) || result.pages() == null
                        || result.pageCount() < 1 || result.pageCount() != result.pages().size() || result.warnings() == null)
                    throw new IllegalArgumentException("Invalid extraction response");
                for (int index = 0; index < result.pages().size(); index++) {
                    var page = result.pages().get(index);
                    if (page == null || page.pageNumber() != index + 1 || page.text() == null || page.blocks() == null)
                        throw new IllegalArgumentException("Invalid extraction page");
                }
                if (includeChunks && result.chunks() == null)
                    throw new IllegalArgumentException("Missing requested chunks");
                if (result.chunks() != null) {
                    for (int index = 0; index < result.chunks().size(); index++) {
                        var chunk = result.chunks().get(index);
                        if (chunk == null || !documentId.equals(chunk.documentId()) || chunk.chunkIndex() != index
                                || chunk.content() == null || chunk.content().isBlank()
                                || chunk.embeddingText() == null || chunk.embeddingText().isBlank()
                                || chunk.headings() == null || chunk.blockIds() == null || chunk.blockIds().isEmpty()
                                || chunk.sourceRefs() == null || chunk.sourceRefs().isEmpty()
                                || chunk.overlapContent() == null || chunk.overlapSourceRefs() == null
                                || chunk.tokenCount() <= 0 || chunk.tokenizerModel() == null || chunk.tokenizerModel().isBlank()
                                || chunk.pageStart() < 1 || chunk.pageEnd() < chunk.pageStart()
                                || chunk.pageEnd() > result.pageCount())
                            throw new IllegalArgumentException("Invalid chunk");
                        var references = java.util.stream.Stream.concat(chunk.sourceRefs().stream(), chunk.overlapSourceRefs().stream());
                        if (references.anyMatch(ref -> ref == null || ref.blockId() == null
                                || !chunk.blockIds().contains(ref.blockId()) || ref.bbox() == null
                                || ref.pageNumber() < chunk.pageStart() || ref.pageNumber() > chunk.pageEnd()))
                            throw new IllegalArgumentException("Invalid chunk provenance");
                    }
                }
            } catch (IOException | IllegalArgumentException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI Service trả kết quả không hợp lệ.");
            }
            var chunks = result.chunks() == null ? null : result.chunks().stream().map(chunk -> chunk.withSource(source)).toList();
            return new DocumentExtractionResponse(documentId, source, result.pageCount(), result.pages(), result.warnings(), chunks);
        } catch (HttpTimeoutException timeoutError) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI Service xử lý quá thời gian cho phép.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Yêu cầu trích xuất bị gián đoạn.");
        } catch (IOException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không kết nối được AI Service.");
        }
    }
}
