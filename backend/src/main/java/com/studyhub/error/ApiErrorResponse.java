package com.studyhub.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiErrorResponse(
        @Schema(description = "Mã lỗi dùng cho client.", example = "VALIDATION_ERROR") String code,
        @Schema(example = "Validation failed") String message,
        @Schema(example = "/api/workspaces") String path,
        @Schema(description = "Thời điểm phát sinh lỗi theo UTC.") Instant timestamp,
        @Schema(description = "Chỉ xuất hiện khi có lỗi validation theo trường.", example = "{\"name\":\"must not be blank\"}")
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fieldErrors
) {}
