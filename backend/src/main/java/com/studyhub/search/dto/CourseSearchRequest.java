package com.studyhub.search.dto;

import jakarta.validation.constraints.*;

public class CourseSearchRequest {
    @NotBlank @Size(max=16384)
    private String query;
    @NotNull @Min(1) @Max(20)
    @io.swagger.v3.oas.annotations.media.Schema(defaultValue="5",requiredMode=io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED,
            description="Bỏ qua thì dùng 5; truyền null hoặc ngoài 1–20 bị từ chối.")
    private Integer topK=5;

    public String getQuery(){return query;}
    public void setQuery(String query){this.query=query;}
    public Integer getTopK(){return topK;}
    public void setTopK(Integer topK){this.topK=topK;}
}
