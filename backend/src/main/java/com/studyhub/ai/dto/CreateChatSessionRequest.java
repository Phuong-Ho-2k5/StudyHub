package com.studyhub.ai.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public class CreateChatSessionRequest {
    @NotBlank @Size(max=150)
    @Schema(type="string",description="Required JSON string, trimmed; 1–150 characters after trimming.")
    private String title;

    public String getTitle() { return title; }
    @JsonSetter("title")
    public void setTitle(Object value) {
        if(value!=null && !(value instanceof String))
            throw new IllegalArgumentException("Title must be a JSON string.");
        title=value==null?null:((String)value).strip();
    }
}
