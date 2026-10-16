package com.studyhub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI studyHubOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("StudyHub API")
                        .version("v1")
                        .description("API quản lý học tập theo Workspace và Course. "
                                + "Đăng ký và đăng nhập không yêu cầu JWT. Các API nghiệp vụ dùng Bearer JWT; "
                                + "API quản trị yêu cầu role ADMIN. Tài nguyên không tồn tại hoặc không thuộc "
                                + "người dùng trả 404. Phân trang dùng page bắt đầu từ 0, size và sort=field,asc|desc; "
                                + "có thể lặp lại sort để sắp xếp theo nhiều trường. Thời gian trả theo UTC."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Nhập accessToken nhận từ POST /api/auth/login, không thêm tiền tố Bearer.")));
    }
}
