package com.studyhub.question.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import com.studyhub.question.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record QuestionRequest(
        @Schema(description = "Nội dung câu hỏi, gồm hướng dẫn định dạng trả lời nếu cần.", example = "HTTP mặc định dùng cổng nào? Chỉ nhập số nguyên.")
        @NotBlank @Size(max = 500) String text,
        @Schema(example = "SHORT_ANSWER") @NotNull QuestionType type,
        @Schema(description = "Bắt buộc có nội dung với SHORT_ANSWER, tối đa 4000 ký tự. Phải null hoặc bỏ trường này với MULTIPLE_CHOICE và TRUE_FALSE. Giữ nguyên hoa/thường, dấu, khoảng trắng và xuống dòng.", example = "80")
        @Size(max = 4000) String referenceAnswer,
        @Schema(description = "SHORT_ANSWER dùng mảng rỗng. MULTIPLE_CHOICE cần ít nhất 4 lựa chọn, ít nhất 1 đúng. TRUE_FALSE cần 2 lựa chọn, đúng 1 đúng.", example = "[]")
        @NotNull List<@NotNull @Valid AnswerOptionRequest> options,
        @Schema(description = "Tùy chọn; Concept phải thuộc cùng Course với Quiz.")
        @Positive Long conceptId,
        @Schema(description = "Required for AI Quiz mutations: revision from GET Quiz review; omitted for MANUAL.")
        @tools.jackson.databind.annotation.JsonDeserialize(using = com.studyhub.quiz.dto.RevisionDeserializer.class)
        @jakarta.validation.constraints.PositiveOrZero Long expectedRevision) {
    public QuestionRequest(String text, QuestionType type, String referenceAnswer, List<AnswerOptionRequest> options, Long conceptId) {
        this(text, type, referenceAnswer, options, conceptId, null);
    }
}
