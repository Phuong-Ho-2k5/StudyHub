package com.studyhub.ai.quiz.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;
import java.util.*;

public record GenerateCourseQuizRequest(String title,int questionCount,List<Long> documentIds,int chunkLimit) {
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static GenerateCourseQuizRequest parse(JsonNode body) {
        QuizRequestFields.object(body,Set.of("title","questionCount","documentIds","chunkLimit"));
        var values=body.path("documentIds");
        if(!values.isArray() || values.isEmpty() || values.size()>5)throw new IllegalArgumentException("Select 1–5 Documents");
        var ids=new TreeSet<Long>();
        for(var id:values)if(!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue()<=0 || !ids.add(id.longValue()))throw new IllegalArgumentException("Invalid or duplicate Document ID");
        int limit=QuizRequestFields.integer(body,"chunkLimit",10,1,20);
        if(limit<ids.size())throw new IllegalArgumentException("chunkLimit must cover every selected Document");
        return new GenerateCourseQuizRequest(QuizRequestFields.title(body),QuizRequestFields.integer(body,"questionCount",5,1,10),List.copyOf(ids),limit);
    }
}
