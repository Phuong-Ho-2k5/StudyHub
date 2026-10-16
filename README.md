# StudyHub

StudyHub là dự án quản lý học tập theo **Workspace → Course**. Phiên bản hiện tại có đăng ký, đăng nhập bằng JWT, CRUD Workspace và Course, cùng quản lý metadata Document, Concept và Quiz trong từng Course. Backend hỗ trợ Question, nộp Quiz, tính điểm, lưu kết quả tổng hợp mỗi lần nộp vào QuizAttempt và quản lý bài thảo luận cùng bình luận trong Course. Giao diện React hỗ trợ các luồng Workspace/Course/Document/Concept, quản lý Quiz/câu hỏi, làm bài và xem kết quả sau đăng nhập. AI/RAG nằm trong kế hoạch phát triển.

## Cấu trúc dự án

| Thư mục | Vai trò hiện tại |
| --- | --- |
| `backend/` | Spring Boot API, xác thực JWT, kiểm tra quyền sở hữu và Flyway migrations |
| `frontend/` | React/Vite cho xác thực, tài nguyên học tập, Quiz, thống kê/tiến độ, phiên học, thảo luận, thông báo và quản trị |
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

## Tài liệu API

Backend cung cấp [Swagger UI](http://localhost:8080/swagger-ui.html) và bản mô tả [OpenAPI JSON](http://localhost:8080/v3/api-docs) / [YAML](http://localhost:8080/v3/api-docs.yaml). Chỉ cần chạy backend để xem tài liệu và gọi API trực tiếp, không cần chạy frontend.

Đăng nhập bằng `POST /api/auth/login`, sao chép `accessToken`, rồi bấm **Authorize** trong Swagger UI và dán token không kèm tiền tố `Bearer`. Xem [hướng dẫn sử dụng](backend/README.md#tài-liệu-api-tương-tác) để biết cách gọi API và quyền truy cập.

## API hiện có

| Nhóm | Endpoint | Chức năng |
| --- | --- | --- |
| Auth | `POST /api/auth/register`, `POST /api/auth/login` | Tạo tài khoản, nhận JWT |
| Workspace | `GET/POST /api/workspaces`, `GET/PUT/DELETE /api/workspaces/{id}` | Quản lý Workspace của người dùng hiện tại |
| Course | `POST /api/workspaces/{workspaceId}/courses` | Tạo Course trong Workspace đã sở hữu |
| Course | `GET /api/courses`, `GET/PUT/DELETE /api/courses/{id}` | Liệt kê, đọc, sửa và xóa Course được phép truy cập |
| Progress | `GET /api/courses/{courseId}/progress` | Tiến độ trung bình confidence và tỷ lệ Concept nắm vững; xem [quy tắc](backend/README.md#tiến-độ-course) |
| Statistics | `GET /api/users/me/statistics` | Tổng thời gian học, số phiên đã kết thúc, số lần làm Quiz và điểm trung bình của user hiện tại; xem [quy tắc](backend/README.md#thống-kê-người-dùng) |
| Quiz History | `GET /api/users/me/quiz-history` | Lịch sử làm Quiz của user hiện tại, phân trang và trả kết quả đã lưu; xem [quy tắc](backend/README.md#lịch-sử-quiz) |
| DiscussionPost | `GET/POST /api/courses/{courseId}/discussion-posts`, `GET/PUT/DELETE /api/discussion-posts/{id}` | CRUD bài thảo luận, phân trang, kiểm tra quyền Course và tác giả; xem [quy tắc](backend/README.md#bài-thảo-luận) |
| Comment | `GET/POST /api/discussion-posts/{postId}/comments`, `GET/PUT/DELETE /api/comments/{id}` | CRUD bình luận, phân trang theo thời gian, kiểm tra quyền Course và tác giả; xem [quy tắc](backend/README.md#bình-luận) |
| Document | `POST /api/courses/{courseId}/documents`, `GET /api/documents`, `GET/PUT/DELETE /api/documents/{id}` | Quản lý metadata tài liệu theo Course |
| Concept | `GET/POST /api/courses/{courseId}/concepts`, `GET /api/concepts`, `GET/PUT/DELETE /api/concepts/{id}` | Quản lý khái niệm và lọc theo mức độ hiểu |
| Prerequisite | `GET/POST /api/concepts/{id}/prerequisites`, `DELETE /api/concepts/{id}/prerequisites/{prerequisiteId}` | Quản lý kiến thức tiên quyết |
| Quiz | `GET/POST /api/courses/{courseId}/quizzes`, `GET/PUT/DELETE /api/quizzes/{id}` | Quản lý Quiz thuộc Course |
| Question | `GET/POST /api/quizzes/{quizId}/questions`, `GET/PUT/DELETE /api/questions/{id}` | Quản lý câu hỏi và đáp án |
| Submit Quiz | `POST /api/quizzes/{quizId}/submit` | Validate, tính điểm và lưu kết quả tổng hợp mỗi lần nộp |
| Health | `GET /actuator/health` | Kiểm tra trạng thái backend |
| Admin | `GET /api/admin/users`, `GET /api/admin/users/{id}`, `PATCH /api/admin/users/{id}/role`, `GET /api/admin/statistics` | Quản lý role người dùng và thống kê toàn hệ thống; yêu cầu `ADMIN`, xem [quy tắc](backend/README.md#api-quản-trị) |

Ngoài đăng ký, đăng nhập và health check, các API yêu cầu header `Authorization: Bearer <accessToken>`. Quyền Course được suy ra từ owner của Workspace; truy cập tài nguyên của người khác trả về 404.

`GET /api/courses` nhận các bộ lọc tùy chọn `workspaceId`, `status` (`PLANNED`, `IN_PROGRESS`, `COMPLETED`, `ARCHIVED`) và `q` (tìm trong tên, không phân biệt chữ hoa/thường). Spring Data hỗ trợ `page` (bắt đầu từ 0), `size` và `sort`:

```text
GET /api/courses?workspaceId=3&status=PLANNED&q=java&page=0&size=10&sort=name,asc
```

Kết quả là một trang dữ liệu có `content`, `totalElements`, `totalPages` và thông tin phân trang. Chi tiết request/response nằm trong các DTO và controller của [backend](backend/src/main/java/com/studyhub/).

## Hướng phát triển

`ai-service/` hiện là bộ khung, chưa xử lý tài liệu hay gọi LLM. Document hiện chỉ lưu metadata, chưa hỗ trợ tải tệp. Backend và frontend Quiz đã hỗ trợ CRUD Question, nộp bài, tính điểm, lưu QuizAttempt và cập nhật confidence của Concept trong cùng transaction. Câu trả lời ngắn (`SHORT_ANSWER`) được chấm ngay bằng cách so khớp từng ký tự với đáp án mẫu, phân biệt chữ hoa/thường, dấu, khoảng trắng và xuống dòng; không gọi AI. Frontend giữ nguyên đáp án mẫu và câu trả lời khi gửi, hướng dẫn người soạn ghi định dạng trả lời trong đề; chưa có trường hướng dẫn riêng. Lịch sử cũ giữ nguyên kết quả đã lưu. Backend đã có API tổng hợp tiến độ Course theo confidence của Concept và API xem lịch sử Quiz của user hiện tại. Frontend đã bổ sung lịch sử Quiz, thống kê học tập, tiến độ Course, phiên học, thảo luận/bình luận, thông báo và quản trị. AI/RAG chưa có giao diện. Backend phải kiểm tra quyền Course trước khi cho các tính năng theo Course đọc dữ liệu; secret, token và tệp tải lên không được đưa vào Git.
