package com.studyhub.ai.feedback;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public class FeedbackRequest {
    @NotNull
    @Schema(description="Exact JSON string HELPFUL or NOT_HELPFUL.")
    private FeedbackRating rating;
    @Size(max=2000)
    @Schema(description="Optional JSON string, stripped; blank becomes null. Maximum 2000 UTF-16 units after stripping. Omitted note clears the old note.",nullable=true)
    private String note;
    public FeedbackRating getRating() {return rating;}
    public String getNote() {return note;}
    @JsonSetter("rating") public void setRating(Object value) {
        if(!(value instanceof String text)) throw new IllegalArgumentException("Rating must be a JSON string.");
        rating=FeedbackRating.valueOf(text);
    }
    @JsonSetter("note") public void setNote(Object value) {
        if(value!=null && !(value instanceof String)) throw new IllegalArgumentException("Note must be a JSON string.");
        note=value==null?null:((String)value).strip();
        if(note!=null && note.isEmpty())note=null;
    }
    @JsonAnySetter public void unknown(String name,Object value) {throw new IllegalArgumentException("Unknown feedback field.");}
}
