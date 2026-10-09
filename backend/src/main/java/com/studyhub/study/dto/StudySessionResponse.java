package com.studyhub.study.dto;

import java.time.Instant;

public record StudySessionResponse(
    Long id,
    Long userId,
    Long courseId,
    Instant startTime,
    Instant endTime,
    Long duration
) {}