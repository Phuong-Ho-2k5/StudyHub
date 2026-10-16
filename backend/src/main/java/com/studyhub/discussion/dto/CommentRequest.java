package com.studyhub.discussion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @Schema(description = "Kiểm tra độ dài trước khi trim hai đầu; không nhận tác giả hoặc bài từ body.", example = "Giúp khai báo dependency rõ ràng.")
        @NotBlank @Size(max = 10000) String content
) {}
