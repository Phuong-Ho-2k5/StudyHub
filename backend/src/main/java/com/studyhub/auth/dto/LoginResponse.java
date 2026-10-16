package com.studyhub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
    @Schema(description = "JWT dùng cho Authorization: Bearer <accessToken>.") String accessToken
){}
