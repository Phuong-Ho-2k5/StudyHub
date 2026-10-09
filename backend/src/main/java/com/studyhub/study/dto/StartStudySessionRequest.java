package com.studyhub.study.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StartStudySessionRequest(
    @NotNull @Positive Long courseId
) {}