# Backend

Ứng dụng Spring Boot 4.1.1 dùng Maven và biên dịch cho Java 21. M1-T01 thiết lập bộ khung, dependency và profile cấu hình; các API nghiệp vụ sẽ được bổ sung ở những task tiếp theo.

## Cấu hình

- `local` (mặc định): PostgreSQL tại `localhost:5432/studyhub`, có thể đổi bằng `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`.
- `prod`: bắt buộc cung cấp ba biến môi trường trên và đặt `SPRING_PROFILES_ACTIVE=prod`.
- `test`: H2 trong bộ nhớ, chỉ dùng khi chạy test.

Dependency đã có Spring MVC, JPA, Security, Validation, Actuator, Flyway và PostgreSQL. Migration SQL sẽ được thêm vào `src/main/resources/db/migration` cùng với entity đầu tiên. Chưa có API đăng nhập/JWT hoặc business endpoint; Spring Security hiện dùng hành vi mặc định.

## Chạy

Yêu cầu JDK 21 trở lên và PostgreSQL đang chạy cho profile `local`. Maven Wrapper đi kèm sẽ tải Maven 3.9.9 nếu máy chưa có.

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Nếu Maven báo `JAVA_HOME` không hợp lệ, đặt biến này trỏ tới **thư mục JDK** (không phải `java.exe`) trước khi chạy lệnh.
