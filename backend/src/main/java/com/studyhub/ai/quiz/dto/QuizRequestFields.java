package com.studyhub.ai.quiz.dto;

import static com.studyhub.ai.concept.ConceptSuggestionModels.normalized;
import java.util.Set;
import tools.jackson.databind.JsonNode;

final class QuizRequestFields {
    private QuizRequestFields() {}
    static void object(JsonNode body,Set<String> fields) {
        if(body==null || !body.isObject())throw new IllegalArgumentException("Expected an object");
        body.propertyNames().forEach(key->{if(!fields.contains(key))throw new IllegalArgumentException("Unknown field");});
    }
    static String title(JsonNode body) {
        if(!body.path("title").isTextual())throw new IllegalArgumentException("Title required");
        return normalized(body.path("title").asText(),200);
    }
    static int integer(JsonNode body,String key,int fallback,int min,int max) {
        if(!body.has(key))return fallback;var value=body.path(key);
        if(!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue()<min || value.intValue()>max)throw new IllegalArgumentException("Invalid "+key);
        return value.intValue();
    }
}
