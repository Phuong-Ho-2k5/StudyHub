package com.studyhub.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.*;

public class CourseChatRequest {
    @NotBlank @Size(max=16384)
    @Schema(type="string", description="Required JSON string; numeric and boolean values are rejected.")
    private String question;
    @NotNull @Min(1) @Max(20)
    @Schema(type="integer", format="int32", defaultValue="5", requiredMode=Schema.RequiredMode.NOT_REQUIRED,
            description="Omitted uses 5; null and values outside 1–20 are rejected.")
    private Integer topK = 5;
    @Positive
    @Schema(type="integer", format="int64", nullable=true,
            description="Optional owned session in this Course. Omitted/null keeps stateless chat. History is stored, not sent as conversation context.")
    private Long sessionId;
    public Long getSessionId() { return sessionId; }
    @JsonSetter("sessionId")
    public void setSessionId(Object value) {
        if(value!=null && !(value instanceof Integer) && !(value instanceof Long))
            throw new IllegalArgumentException("sessionId must be a JSON integer within the Long range.");
        sessionId=value==null?null:((Number)value).longValue();
    }
    public String getQuestion() { return question; }
    @JsonSetter("question")
    public void setQuestion(Object question) {
        if (question != null && !(question instanceof String))
            throw new IllegalArgumentException("Question must be a JSON string.");
        this.question = (String) question;
    }
    public Integer getTopK() { return topK; }
    @JsonSetter("topK")
    public void setTopK(Object topK) {
        if (topK != null && !(topK instanceof Integer))
            throw new IllegalArgumentException("topK must be a JSON integer.");
        this.topK = (Integer) topK;
    }
}
