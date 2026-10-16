package com.studyhub.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SubmitAnswerRequest(
        @Schema(description = "ID câu hỏi trong Quiz; mỗi câu được trả lời đúng một lần.", example = "1")
        @NotNull @Positive Long questionId,
        @Schema(description = "Dùng cho MULTIPLE_CHOICE và TRUE_FALSE; lựa chọn phải thuộc câu hỏi. SHORT_ANSWER không dùng trường này.")
        List<@NotNull @Positive Long> answerOptionIds,
        @Schema(description = "Dùng cho SHORT_ANSWER, bắt buộc có nội dung. So khớp nguyên chuỗi với đáp án mẫu, không trim hay chuẩn hóa.", example = "80")
        @Size(max = 4000) String answerText) {}
