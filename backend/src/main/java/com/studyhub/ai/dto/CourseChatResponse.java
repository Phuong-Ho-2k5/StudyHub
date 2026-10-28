package com.studyhub.ai.dto;

public record CourseChatResponse(Long courseId, String question, String status, String answer,
        String model, String promptVersion) {}
