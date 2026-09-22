# Backend

Spring Boot backend chịu trách nhiệm API công khai, JWT, kiểm tra ownership, transaction và các business rule. Code dự kiến nằm trong `src/main/java/com/studyhub` và migration tại `src/main/resources/db/migration`.

Các package được để sẵn theo miền nghiệp vụ. M1 ưu tiên `auth`, `workspace`, `course`; các module còn lại triển khai theo kế hoạch Excel. Chưa chọn phiên bản Spring Boot hoặc công cụ build trong skeleton này.
