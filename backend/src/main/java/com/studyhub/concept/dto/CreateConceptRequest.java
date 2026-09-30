package com.studyhub.concept.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.studyhub.concept.ConceptStatus;

public record CreateConceptRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 1000) String description,
    @NotNull @Min(0) @Max(100) Integer confidence,
    @NotNull ConceptStatus status
) {}
