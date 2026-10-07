# StudyHub

StudyHub là dự án quản lý học tập theo **Workspace → Course**. Phiên bản hiện tại có đăng ký, đăng nhập bằng JWT, CRUD Workspace và Course, cùng quản lý metadata Document, Concept và Quiz trong từng Course. Backend hỗ trợ Question, nộp Quiz, tính điểm và lưu kết quả tổng hợp mỗi lần nộp vào QuizAttempt. Giao diện React hỗ trợ các luồng Workspace/Course/Document/Concept sau đăng nhập. AI/RAG nằm trong kế hoạch phát triển.

## Cấu trúc dự án

| Thư mục | Vai trò hiện tại |
| --- | --- |
| `backend/` | Spring Boot API, xác thực JWT, kiểm tra quyền sở hữu và Flyway migrations |
| `frontend/` | React/Vite cho xác thực và quản lý Workspace/Course/Document/Concept |
| `ai-service/` | Bộ khung cho dịch vụ AI/RAG trong tương lai |
| `infra/` | Vị trí dành cho cấu hình hạ tầng trong tương lai |

## Chạy local

Yêu cầu: **JDK 21 trở lên**, **PostgreSQL** và **Node.js 20.19+ hoặc 22.12+**. Tạo database `studyhub` trước khi chạy backend. Repo chưa có Docker Compose để khởi tạo database tự động.

1. Sao chép `.env.example` thành `.env` tại thư mục gốc và điền tối thiểu:

   ```properties
   POSTGRES_USER=studyhub
   POSTGRES_PASSWORD=<mật khẩu PostgreSQL>
   DATABASE_URL=jdbc:postgresql://localhost:5432/studyhub
   JWT_SECRET=<chuỗi Base64 của ít nhất 32 byte ngẫu nhiên>
   ```

   `JWT_EXPIRATION` tùy chọn, tính bằng mili giây; mặc định là `900000` (15 phút). Không commit `.env`. Profile `local` đọc file này khi chạy từ thư mục `backend/`.

2. Chạy backend trong một terminal:

   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

   Backend mặc định ở `http://localhost:8080`. Flyway áp dụng các migration tạo bảng `users`, `workspaces`, `courses`; Hibernate kiểm tra schema khi khởi động. Nếu Maven báo `JAVA_HOME` không hợp lệ, đặt biến này trỏ tới **thư mục JDK**, không phải file `java.exe`.

3. Chạy frontend trong terminal khác:

   ```powershell
   cd frontend
   npm ci
   npm run dev
   ```

   Mở `http://localhost:5173`. Vite chuyển tiếp `/api` tới backend ở cổng 8080. Khi frontend và backend dùng hai origin khác nhau, đặt `VITE_API_URL` trong `frontend/.env.local`; xem [hướng dẫn frontend](frontend/README.md).

Trên macOS/Linux, dùng `./mvnw` thay cho `.\mvnw.cmd`.

## API hiện có

| Nhóm | Endpoint | Chức năng |
| --- | --- | --- |
| Auth | `POST /api/auth/register`, `POST /api/auth/login` | Tạo tài khoản, nhận JWT |
| Workspace | `GET/POST /api/workspaces`, `GET/PUT/DELETE /api/workspaces/{id}` | Quản lý Workspace của người dùng hiện tại |
| Course | `POST /api/workspaces/{workspaceId}/courses` | Tạo Course trong Workspace đã sở hữu |
| Course | `GET /api/courses`, `GET/PUT/DELETE /api/courses/{id}` | Liệt kê, đọc, sửa và xóa Course được phép truy cập |
| Document | `POST /api/courses/{courseId}/documents`, `GET /api/documents`, `GET/PUT/DELETE /api/documents/{id}` | Quản lý metadata tài liệu theo Course |
| Concept | `GET/POST /api/courses/{courseId}/concepts`, `GET /api/concepts`, `GET/PUT/DELETE /api/concepts/{id}` | Quản lý khái niệm và lọc theo mức độ hiểu |
| Prerequisite | `GET/POST /api/concepts/{id}/prerequisites`, `DELETE /api/concepts/{id}/prerequisites/{prerequisiteId}` | Quản lý kiến thức tiên quyết |
| Quiz | `GET/POST /api/courses/{courseId}/quizzes`, `GET/PUT/DELETE /api/quizzes/{id}` | Quản lý Quiz thuộc Course |
| Question | `GET/POST /api/quizzes/{quizId}/questions`, `GET/PUT/DELETE /api/questions/{id}` | Quản lý câu hỏi và đáp án |
| Submit Quiz | `POST /api/quizzes/{quizId}/submit` | Validate, tính điểm và lưu kết quả tổng hợp mỗi lần nộp |
| Health | `GET /actuator/health` | Kiểm tra trạng thái backend |

Ngoài đăng ký, đăng nhập và health check, các API yêu cầu header `Authorization: Bearer <accessToken>`. Quyền Course được suy ra từ owner của Workspace; truy cập tài nguyên của người khác trả về 404.

`GET /api/courses` nhận các bộ lọc tùy chọn `workspaceId`, `status` (`PLANNED`, `IN_PROGRESS`, `COMPLETED`, `ARCHIVED`) và `q` (tìm trong tên, không phân biệt chữ hoa/thường). Spring Data hỗ trợ `page` (bắt đầu từ 0), `size` và `sort`:

```text
GET /api/courses?workspaceId=3&status=PLANNED&q=java&page=0&size=10&sort=name,asc
```

Kết quả là một trang dữ liệu có `content`, `totalElements`, `totalPages` và thông tin phân trang. Chi tiết request/response nằm trong các DTO và controller của [backend](backend/src/main/java/com/studyhub/).

## Kiểm tra

```powershell
cd backend
.\mvnw.cmd test
```

Test backend dùng H2 ở chế độ tương thích PostgreSQL. Bộ test hiện kiểm tra luồng Workspace và Course, gồm quyền owner, CRUD Course, lọc kết hợp, phân trang và sắp xếp.

```powershell
cd frontend
npm test
npm run lint
npm run build
```

## Hướng phát triển

`ai-service/` hiện là bộ khung, chưa xử lý tài liệu hay gọi LLM. Document hiện chỉ lưu metadata, chưa hỗ trợ tải tệp. Backend Quiz đã hỗ trợ CRUD Question, nộp bài, tính điểm và lưu QuizAttempt; câu tự luận ngắn hiện chờ chấm. API xem lịch sử, cập nhật confidence của Concept, theo dõi tiến độ và AI/RAG sẽ được bổ sung ở các task sau. Backend phải kiểm tra quyền Course trước khi cho các tính năng này đọc dữ liệu; secret, token và tệp tải lên không được đưa vào Git.
