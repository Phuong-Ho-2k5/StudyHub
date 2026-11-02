package com.studyhub.ai.concept.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

public record GenerateConceptSuggestionsRequest(int startChunkIndex,int chunkLimit,int maxSuggestions) {
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static GenerateConceptSuggestionsRequest parse(JsonNode body) {
        if(body==null || !body.isObject()) throw new IllegalArgumentException("Expected an object");
        body.propertyNames().forEach(key->{
            if(!java.util.Set.of("startChunkIndex","chunkLimit","maxSuggestions").contains(key))
                throw new IllegalArgumentException("Unknown field");
        });
        return new GenerateConceptSuggestionsRequest(integer(body,"startChunkIndex",0,0,Integer.MAX_VALUE),
            integer(body,"chunkLimit",10,1,20),integer(body,"maxSuggestions",5,1,10));
    }
    private static int integer(JsonNode body,String key,int fallback,int min,int max) {
        if(!body.has(key)) return fallback;
        var value=body.path(key);
        if(!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue()<min || value.intValue()>max)
            throw new IllegalArgumentException("Invalid "+key);
        return value.intValue();
    }
}
