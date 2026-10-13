package com.studyhub.discussion.dto;

import java.sql.Timestamp;

public record CommentResponse(
        Long id,
        Long postId,
        Long authorId,
        String content,
        Timestamp createdAt,
        Timestamp updatedAt
) {}
