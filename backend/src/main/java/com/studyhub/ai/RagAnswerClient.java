package com.studyhub.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyhub.search.dto.CourseSearchResponse.SearchHitResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RagAnswerClient {
    public record RagAnswerResult(String status, String answer, String model, String promptVersion) {}
    private record Context(Long chunkId, Long documentId, String documentTitle, int chunkIndex,
            String content, Integer pageStart, Integer pageEnd) {}
    private record Request(String question, List<Context> contexts) {}
    // Uvicorn serves HTTP/1.1; h2c upgrade requests can lose the initial request body.
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String url, token, model;
    private final Duration timeout;

    public RagAnswerClient(@Value("${studyhub.ai.url:http://localhost:8000}") String url,
            @Value("${studyhub.ai.token:}") String token,
            @Value("${studyhub.ai.chat-timeout-seconds:75}") long timeout,
            @Value("${studyhub.ai.llm-model:gpt-4.1-mini}") String model) {
        if (timeout <= 0) throw new IllegalArgumentException("Invalid chat timeout");
        this.url = url.replaceAll("/+$", ""); this.token = token; this.model = model; this.timeout = Duration.ofSeconds(timeout);
    }

    public RagAnswerResult answer(String question, List<SearchHitResponse> contexts) {
        if (url.isBlank() || token.isBlank() || model.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI chat is not configured.");
        var body = new Request(question, contexts.stream().map(hit -> new Context(hit.chunkId(), hit.documentId(),
                hit.documentTitle(), hit.chunkIndex(), hit.content(), hit.pageStart(), hit.pageEnd())).toList());
        try {
            var request = HttpRequest.newBuilder(URI.create(url + "/internal/rag/answer")).timeout(timeout)
                    .header("X-AI-Service-Token", token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                boolean budget = false;
                if (response.statusCode() == 422) {
                    try { budget = "TOKEN_BUDGET_EXCEEDED".equals(json.readTree(response.body()).at("/detail/code").asText()); }
                    catch (IOException | NullPointerException ignored) { /* malformed internal validation is a gateway error */ }
                }
                var status = switch (response.statusCode()) {
                    case 401, 403, 429, 503 -> HttpStatus.SERVICE_UNAVAILABLE;
                    case 504 -> HttpStatus.GATEWAY_TIMEOUT;
                    case 422 -> budget ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_GATEWAY;
                    default -> HttpStatus.BAD_GATEWAY;
                };
                throw new ResponseStatusException(status, "AI chat request failed.");
            }
            try {
                var root = json.readTree(response.body());
                if (root == null || !root.isObject()) throw invalid();
                for (String key : List.of("status", "answer", "model", "promptVersion"))
                    if (!root.path(key).isTextual() || root.path(key).asText().isBlank()) throw invalid();
                String status = root.path("status").asText();
                if (!(status.equals("ANSWERED") || status.equals("INSUFFICIENT_CONTEXT"))
                        || !model.equals(root.path("model").asText())
                        || !CourseChatService.PROMPT_VERSION.equals(root.path("promptVersion").asText())
                        || root.path("answer").asText().length() > 32768) throw invalid();
                return new RagAnswerResult(status, root.path("answer").asText(), model, CourseChatService.PROMPT_VERSION);
            } catch (IOException malformed) { throw invalid(); }
        } catch (HttpTimeoutException deadline) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI chat request timed out.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI chat request was interrupted.");
        } catch (IOException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI chat is unavailable.");
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI chat returned an invalid answer.");
    }
}
