package com.studyhub.ai.advice;

import com.fasterxml.jackson.annotation.JsonCreator;
import static com.studyhub.ai.concept.ConceptSuggestionModels.normalized;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdviceRequest(
    @Schema(description="Yêu cầu tư vấn học tập, 1–2000 ký tự sau trim",requiredMode=Schema.RequiredMode.REQUIRED) String question,
    @Schema(description="Số chunk truy xuất, integer 1–10; bỏ qua dùng 5",defaultValue="5") int topK) {
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static AdviceRequest parse(JsonNode body) {
        if (body==null || !body.isObject()) throw new IllegalArgumentException("Expected an object");
        body.propertyNames().forEach(key -> { if (!Set.of("question","topK").contains(key)) throw new IllegalArgumentException("Unknown field"); });
        if (!body.path("question").isTextual()) throw new IllegalArgumentException("Question must be a string");
        String question=normalized(body.path("question").asText(),2000);
        int topK=5;
        if (body.has("topK")) {
            var value=body.path("topK");
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue()<1 || value.intValue()>10)
                throw new IllegalArgumentException("topK must be an integer from 1 to 10");
            topK=value.intValue();
        }
        return new AdviceRequest(question,topK);
    }
}
