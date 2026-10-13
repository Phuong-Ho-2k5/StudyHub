package com.studyhub.discussion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DiscussionPostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String content
) {}
