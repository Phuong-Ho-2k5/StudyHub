package com.studyhub.error;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiErrorResponse(
        String code,
        String message,
        String path,
        Instant timestamp,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fieldErrors
) {}
