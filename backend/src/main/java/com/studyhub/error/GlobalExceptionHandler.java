package com.studyhub.error;

import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import com.studyhub.auth.exception.DuplicateEmailException;
import com.studyhub.auth.exception.UserNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ApiErrorFactory factory;

    public GlobalExceptionHandler(ApiErrorFactory factory) {
        this.factory = factory;
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiErrorResponse> duplicateEmail(DuplicateEmailException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({BadCredentialsException.class, UserNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> invalidCredentials(RuntimeException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid email or password", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected API error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error", request.getRequestURI());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage() == null
                        ? "Invalid value" : error.getDefaultMessage()));
        ApiErrorResponse body = factory.create(status, "VALIDATION_ERROR", "Validation failed",
                path(request), fieldErrors);
        return new ResponseEntity<>(body, headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String message = status instanceof HttpStatus httpStatus ? httpStatus.getReasonPhrase() : "Request failed";
        if (ex instanceof ResponseStatusException responseStatusException
                && responseStatusException.getReason() != null
                && status.is4xxClientError()) {
            message = responseStatusException.getReason();
        }
        if (status.is5xxServerError()) {
            log.error("Unexpected API error", ex);
            message = "Internal server error";
        }
        return new ResponseEntity<>(factory.create(status, factory.codeFor(status), message, path(request)),
                headers, status);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message, String path) {
        return ResponseEntity.status(status).body(factory.create(status, code, message, path));
    }

    private String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
