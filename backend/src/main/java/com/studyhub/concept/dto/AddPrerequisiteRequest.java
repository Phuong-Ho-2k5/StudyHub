package com.studyhub.concept.dto;

import jakarta.validation.constraints.NotNull;

public record AddPrerequisiteRequest(
    @NotNull Long prerequisiteId
) {}