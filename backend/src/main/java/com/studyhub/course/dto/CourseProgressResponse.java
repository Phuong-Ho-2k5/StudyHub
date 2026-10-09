package com.studyhub.course.dto;

public record CourseProgressResponse(
        Long courseId,
        int totalConcepts,
        double progressPercentage,
        int masteredConcepts,
        int developingConcepts,
        int needsReviewConcepts,
        double masteryPercentage
) {}
