package com.studyhub.quiz.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Prevent JSON numeric/string coercion for the concurrency token only. */
public class RevisionDeserializer extends ValueDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT)
            throw new IllegalArgumentException("expectedRevision must be a nonnegative integer");
        long value = parser.getLongValue();
        if (value < 0) throw new IllegalArgumentException("expectedRevision must be nonnegative");
        return value;
    }
}
