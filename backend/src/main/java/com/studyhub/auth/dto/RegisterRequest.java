package com.studyhub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @Schema(example = "learner@example.com")
    @Email @NotBlank String email,
    @Schema(description = "Mật khẩu từ 8 đến 100 ký tự.", format = "password", example = "ExamplePass123!")
    @NotBlank @Size(min = 8, max = 100) String password,
    @Schema(example = "Nguyễn An")
    @NotBlank String name
){}
