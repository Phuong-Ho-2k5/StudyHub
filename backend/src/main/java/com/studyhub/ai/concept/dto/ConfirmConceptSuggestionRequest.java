package com.studyhub.ai.concept.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;
import static com.studyhub.ai.concept.ConceptSuggestionModels.normalized;

public record ConfirmConceptSuggestionRequest(String name,String description) {
    public ConfirmConceptSuggestionRequest {
        name=normalized(name,150);description=normalized(description,1000);
    }
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static ConfirmConceptSuggestionRequest parse(JsonNode body) {
        if(body==null || !body.isObject() || body.size()!=2 || !body.path("name").isTextual() || !body.path("description").isTextual())
            throw new IllegalArgumentException("Only name and description are accepted");
        return new ConfirmConceptSuggestionRequest(body.path("name").asText(),body.path("description").asText());
    }
}
