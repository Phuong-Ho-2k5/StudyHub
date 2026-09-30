package com.studyhub.concept.dto;

import com.studyhub.concept.ConceptStatus;

public record ConceptResponse(
    Long id,
    Long courseId,
    String name,
    String description,
    Integer confidence,
    ConceptStatus status
) {}