package com.studyhub.discussion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DiscussionPostRequest(
        @Schema(description = "Kiểm tra độ dài trước khi trim hai đầu.", example = "Constructor injection")
        @NotBlank @Size(max = 200) String title,
        @Schema(description = "Trim hai đầu khi lưu, giữ nguyên khoảng trắng và xuống dòng bên trong.", example = "Constructor injection có lợi ích gì?")
        @NotBlank @Size(max = 10000) String content
) {}
