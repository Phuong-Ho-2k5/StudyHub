# StudyHub Backend

Backend dùng Spring Boot 4.1.1, Java 21, Maven, PostgreSQL, Flyway và JWT. Các API hiện có gồm Auth, Workspace và Course. Xem [README gốc](../README.md) để chạy cả backend lẫn frontend.

## Cấu hình

Profile `local` là mặc định. Khi chạy từ thư mục `backend/`, ứng dụng đọc `../.env` nếu có. Các biến cần thiết:

| Biến | Mục đích |
| --- | --- |
| `DATABASE_URL` | JDBC URL, ví dụ `jdbc:postgresql://localhost:5432/studyhub` |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | Tài khoản PostgreSQL |
| `JWT_SECRET` | Khóa JWT dạng Base64, giải mã thành ít nhất 32 byte |
| `JWT_EXPIRATION` | Thời hạn token tính bằng mili giây; mặc định `900000` |

Profile `prod` yêu cầu các biến database được cung cấp qua môi trường và chọn bằng `SPRING_PROFILES_ACTIVE=prod`. Test tích hợp dùng H2 trong bộ nhớ. Flyway tạo bảng qua `V1__create_users.sql`, `V2__create_workspaces.sql`, `V3__create_courses.sql`; JPA dùng `ddl-auto: validate`.

## Chạy và kiểm tra

Tạo database PostgreSQL và cấu hình `.env` ở gốc repo trước khi chạy local:

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
```

Trên macOS/Linux, dùng `./mvnw`. `JAVA_HOME` cần trỏ tới thư mục JDK 21 trở lên.

## API

- `POST /api/auth/register`, `POST /api/auth/login`: đăng ký và nhận JWT.
- `GET/POST /api/workspaces`, `GET/PUT/DELETE /api/workspaces/{id}`: CRUD Workspace theo owner.
- `POST /api/workspaces/{workspaceId}/courses`, `GET /api/courses`, `GET/PUT/DELETE /api/courses/{id}`: CRUD Course theo owner Workspace.
- `GET /actuator/health`: health check.

Các endpoint nghiệp vụ yêu cầu `Authorization: Bearer <accessToken>`. `GET /api/courses` nhận `workspaceId`, `status`, `q`, `page`, `size`, `sort`; ví dụ:

```text
/api/courses?status=IN_PROGRESS&q=java&page=0&size=10&sort=name,asc
```

Test tích hợp trong `src/test/java/com/studyhub/` kiểm tra quyền truy cập và các luồng API hiện có.

## Cấu trúc lỗi API

Các lỗi dưới `/api/**` trả về JSON cùng cấu trúc. HTTP status nằm ở response; body có `code`, `message`, `path`, `timestamp` (UTC, ISO-8601). Lỗi Bean Validation có thêm `fieldErrors` theo tên trường. Frontend hiện tại hiển thị `message`.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "path": "/api/workspaces",
  "timestamp": "2026-10-02T05:00:00Z",
  "fieldErrors": { "name": "must not be blank" }
}
```

| HTTP status | `code` | Trường hợp |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Bean Validation trên body |
| 400 | `INVALID_REQUEST` | JSON/parameter sai hoặc quy tắc nghiệp vụ bị vi phạm |
| 401 | `UNAUTHORIZED` | Chưa xác thực, JWT sai/hết hạn, hoặc sai thông tin đăng nhập |
| 403 | `FORBIDDEN` | Bị chặn ở tầng Spring Security |
| 404 | `NOT_FOUND` | Endpoint/tài nguyên không tồn tại hoặc không thuộc owner |
| 405 | `METHOD_NOT_ALLOWED` | HTTP method không được hỗ trợ |
| 409 | `CONFLICT` | Email đã tồn tại |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Content-Type không được hỗ trợ |
| 5xx | `INTERNAL_ERROR` | Lỗi server ngoài dự kiến, `message` không lộ chi tiết nội bộ |

Các HTTP status khác có mã `HTTP_<status>`. Response không trả token, stack trace hay giá trị input bị từ chối. `/actuator/health` tiếp tục dùng response của Actuator.
