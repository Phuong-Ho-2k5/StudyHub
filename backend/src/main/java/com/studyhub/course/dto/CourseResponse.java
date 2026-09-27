package com.studyhub.course.dto;

import com.studyhub.course.CourseStatus;

public record CourseResponse(
    Long id,
    String name,
    String description,
    CourseStatus status
){}