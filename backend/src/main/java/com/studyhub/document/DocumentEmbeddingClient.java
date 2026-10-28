package com.studyhub.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DocumentEmbeddingClient {
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;
    private final String token;
    private final Duration timeout;
    private final String expectedModel;
    private final int expectedDimensions;

    public record EmbeddingBatch(String model, int dimensions, List<List<Double>> vectors) {}

    public DocumentEmbeddingClient(@Value("${studyhub.ai.url:http://localhost:8000}") String url,
            @Value("${studyhub.ai.token:}") String token,
            @Value("${studyhub.ai.timeout-seconds:240}") long timeout,
            @Value("${studyhub.ai.embedding-model:intfloat/multilingual-e5-small@614241f622f53c4eeff9890bdc4f31cfecc418b3}") String model,
            @Value("${studyhub.ai.embedding-dimensions:384}") int dimensions) {
        if (timeout <= 0 || dimensions <= 0 || dimensions > 16000 || model == null || model.isBlank() || model.length() > 255)
            throw new IllegalArgumentException("Invalid embedding configuration");
        this.baseUrl = url.replaceAll("/+$", "");
        this.token = token;
        this.timeout = Duration.ofSeconds(timeout);
        this.expectedModel = model;
        this.expectedDimensions = dimensions;
    }

    public void requireTokenizer(String tokenizerModel) {
        String baseModel = expectedModel.split("@", 2)[0];
        if (!baseModel.equals(tokenizerModel)) throw invalidResponse();
    }

    public EmbeddingBatch embed(List<String> texts) {
        return embed(texts, false);
    }

    public String expectedModel() { return expectedModel; }
    public int expectedDimensions() { return expectedDimensions; }

    public EmbeddingBatch embedQuery(String query) {
        if (query == null || query.isBlank() || query.length() > 16384)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must contain 1 to 16384 characters.");
        return embed(List.of(query.strip()), true);
    }

    private EmbeddingBatch embed(List<String> texts, boolean query) {
        if (baseUrl.isBlank() || token.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service is not configured.");
        if (texts == null || texts.isEmpty() || texts.size() > 32
                || texts.stream().anyMatch(text -> text == null || text.isBlank() || text.length() > 16384))
            throw invalidResponse();
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/internal/embeddings"))
                    .timeout(timeout).header("X-AI-Service-Token", token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(query
                            ? Map.of("texts", texts, "inputType", "query") : Map.of("texts", texts)))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                HttpStatus status = switch (response.statusCode()) {
                    case 422 -> query ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_GATEWAY;
                    case 401, 403, 503 -> HttpStatus.SERVICE_UNAVAILABLE;
                    case 504 -> HttpStatus.GATEWAY_TIMEOUT;
                    default -> HttpStatus.BAD_GATEWAY;
                };
                throw new ResponseStatusException(status, "AI embedding request failed.");
            }
            try {
                var root = json.readTree(response.body());
                if (root == null || !root.isObject() || !root.path("model").isTextual()
                        || !expectedModel.equals(root.path("model").textValue())
                        || !root.path("dimensions").isIntegralNumber()
                        || !root.path("dimensions").canConvertToInt()
                        || root.path("dimensions").intValue() != expectedDimensions
                        || !root.path("vectors").isArray() || root.path("vectors").size() != texts.size())
                    throw invalidResponse();
                var vectors = new ArrayList<List<Double>>();
                for (var vector : root.path("vectors")) {
                    if (!vector.isArray() || vector.size() != expectedDimensions) throw invalidResponse();
                    boolean nonzero = false;
                    var values = new ArrayList<Double>();
                    for (var coordinate : vector) {
                        if (!coordinate.isNumber()) throw invalidResponse();
                        double value = coordinate.doubleValue();
                        // PostgreSQL vector stores float32; validate the stored representation too.
                        if (!Double.isFinite(value) || !Float.isFinite((float)value)) throw invalidResponse();
                        nonzero |= (float)value != 0;
                        values.add(value);
                    }
                    if (!nonzero) throw invalidResponse();
                    vectors.add(List.copyOf(values));
                }
                return new EmbeddingBatch(expectedModel, expectedDimensions, List.copyOf(vectors));
            } catch (IOException | IllegalArgumentException malformed) {
                throw invalidResponse();
            }
        } catch (HttpTimeoutException timeoutError) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI embedding request timed out.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI embedding request was interrupted.");
        } catch (IOException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service is unavailable.");
        }
    }

    private static ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service returned invalid embeddings.");
    }
}
