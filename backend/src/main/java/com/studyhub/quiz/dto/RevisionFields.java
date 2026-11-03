package com.studyhub.quiz.dto;

import tools.jackson.databind.JsonNode;

public final class RevisionFields {
    private RevisionFields() {}
    public static long required(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0)
            throw new IllegalArgumentException("expectedRevision must be a nonnegative integer");
        return value.longValue();
    }
}
