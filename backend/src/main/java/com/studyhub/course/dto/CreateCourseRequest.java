package com.studyhub.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Size(max = 1000) String description
){}