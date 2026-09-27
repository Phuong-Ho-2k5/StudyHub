package com.studyhub.course.dto;

import com.studyhub.course.CourseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCourseRequest(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Size(max = 1000) String description,
    @NotNull CourseStatus status
) {}