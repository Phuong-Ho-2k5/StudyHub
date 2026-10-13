package com.studyhub.discussion.dto;

import java.sql.Timestamp;

public record DiscussionPostResponse(
        Long id,
        Long courseId,
        Long authorId,
        String title,
        String content,
        Timestamp createdAt,
        Timestamp updatedAt
) {}
