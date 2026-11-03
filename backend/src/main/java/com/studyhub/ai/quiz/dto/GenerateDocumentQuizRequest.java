package com.studyhub.ai.quiz.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;
import java.util.Set;

public record GenerateDocumentQuizRequest(String title,int questionCount,int startChunkIndex,int chunkLimit) {
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static GenerateDocumentQuizRequest parse(JsonNode body) {
        QuizRequestFields.object(body,Set.of("title","questionCount","startChunkIndex","chunkLimit"));
        return new GenerateDocumentQuizRequest(QuizRequestFields.title(body),QuizRequestFields.integer(body,"questionCount",5,1,10),
            QuizRequestFields.integer(body,"startChunkIndex",0,0,Integer.MAX_VALUE),QuizRequestFields.integer(body,"chunkLimit",10,1,20));
    }
}
