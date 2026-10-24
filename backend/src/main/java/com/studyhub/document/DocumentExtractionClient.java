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
        if (token.isBlank() || baseUrl.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI Service chưa được cấu hình.");
        String boundary = "StudyHub-" + UUID.randomUUID();
        try {
            var body = new ByteArrayOutputStream();
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
            } catch (IOException | IllegalArgumentException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI Service trả kết quả không hợp lệ.");
            }
            return new DocumentExtractionResponse(documentId, source, result.pageCount(), result.pages(), result.warnings());
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
