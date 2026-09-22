# Backend

Ứng dụng Spring Boot 4.1.1 dùng Maven và biên dịch cho Java 21. M1-T01 thiết lập bộ khung, dependency và profile cấu hình; các API nghiệp vụ sẽ được bổ sung ở những task tiếp theo.

## Cấu hình

- `local` (mặc định): PostgreSQL tại `localhost:5432/studyhub`, có thể đổi bằng `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`.
- `prod`: bắt buộc cung cấp ba biến môi trường trên và đặt `SPRING_PROFILES_ACTIVE=prod`.
- `test`: H2 trong bộ nhớ, chỉ dùng khi chạy test.

Dependency đã có Spring MVC, JPA, Security, Validation, Actuator, Flyway và PostgreSQL. Migration `src/main/resources/db/migration/V1__create_users.sql` tạo bảng `users` trên database mới. Chưa có API đăng nhập/JWT hoặc business endpoint; Spring Security hiện dùng hành vi mặc định.

## Chạy

Yêu cầu JDK 21 trở lên và PostgreSQL đang chạy cho profile `local`. Maven Wrapper đi kèm sẽ tải Maven 3.9.9 nếu máy chưa có.

Trước khi chạy lần đầu, tạo database có tên khớp với `DATABASE_URL`. Sao chép `.env.example` thành `.env` tại gốc `StudyHub`, rồi điền `POSTGRES_USER`, `POSTGRES_PASSWORD` và `DATABASE_URL` theo dạng `KEY=value` (không bọc giá trị bằng dấu nháy). Profile `local` tự nạp file này khi chạy từ thư mục `backend`; biến môi trường đã đặt trong hệ thống được ưu tiên hơn giá trị trong file. Không commit `.env`.

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Nếu Maven báo `JAVA_HOME` không hợp lệ, đặt biến này trỏ tới **thư mục JDK** (không phải `java.exe`) trước khi chạy lệnh.
