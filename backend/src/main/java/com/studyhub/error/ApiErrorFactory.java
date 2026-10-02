package com.studyhub.error;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorFactory {
    public ApiErrorResponse create(HttpStatusCode status, String code, String message, String path) {
        return create(status, code, message, path, Map.of());
    }

    public ApiErrorResponse create(HttpStatusCode status, String code, String message, String path,
            Map<String, String> fieldErrors) {
        return new ApiErrorResponse(code, message, path, Instant.now(), fieldErrors);
    }

    public String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "INVALID_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "HTTP_" + status.value();
        };
    }
}
