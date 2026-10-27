# StudyHub

StudyHub là dự án quản lý học tập theo **Workspace → Course**. Phiên bản hiện tại có đăng ký, đăng nhập bằng JWT, CRUD Workspace và Course, cùng quản lý metadata Document, Concept và Quiz trong từng Course. Backend hỗ trợ Question, nộp Quiz, tính điểm, lưu kết quả tổng hợp mỗi lần nộp vào QuizAttempt và quản lý bài thảo luận cùng bình luận trong Course. Giao diện React hỗ trợ các luồng Workspace/Course/Document/Concept, quản lý Quiz/câu hỏi, làm bài và xem kết quả sau đăng nhập. AI/RAG nằm trong kế hoạch phát triển.

Hỗ trợ Docling HybridChunker với heading context và nguồn qua `POST /api/documents/{id}/extract?includeChunks=true`.

Upload PDF tự tạo tác vụ ingestion bền vững: chia đoạn, sinh vector E5 và công bố chỉ mục vào pgvector. `POST /api/documents/{id}/ingest` hỗ trợ xử lý lại; lỗi giữ phiên bản cũ. `POST /api/courses/{courseId}/search` tìm tối đa K đoạn gần nghĩa, chỉ trong Course người dùng sở hữu và trên phiên bản đã công bố. Xem [hướng dẫn ingestion](backend/README.md#ingestion-tài-liệu), [tìm kiếm ngữ nghĩa](backend/README.md#tìm-kiếm-ngữ-nghĩa-theo-course) và [cấu hình mô hình](ai-service/README.md#embedding-và-lưu-chunk). RAG Chat và giao diện AI là các bước tiếp theo.

## Cấu trúc dự án

| Thư mục | Vai trò hiện tại |
| --- | --- |
| `backend/` | Spring Boot API, xác thực JWT, kiểm tra quyền sở hữu và Flyway migrations |
| `frontend/` | React/Vite cho xác thực, tài nguyên học tập, Quiz, thống kê/tiến độ, phiên học, thảo luận, thông báo và quản trị |
| `ai-service/` | FastAPI, Docling PDF/OCR theo trang, mô tả hình tùy chọn và OpenAPI |
| `infra/` | Tài liệu hạ tầng; Docker Compose hiện nằm ở thư mục gốc |

## Chạy local

Yêu cầu: **JDK 21 trở lên**, **PostgreSQL có pgvector** và **Node.js 20.19+ hoặc 22.12+**. Tạo database `studyhub` trước khi chạy backend. Có thể dùng [Docker Compose](#chạy-bằng-docker) để chạy backend và database thay cho cài Java/PostgreSQL trực tiếp. Compose đã dùng PostgreSQL 17 có pgvector; xem [migration và kiểm thử vector](infra/README.md#pgvector-và-documentchunk).

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

## Chạy bằng Docker

Yêu cầu Docker Desktop đang chạy Linux containers và Docker Compose. Thực hiện từ thư mục gốc dự án.

1. Nếu chưa có `.env`, sao chép `.env.example` thành `.env`. Điền `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` và `JWT_SECRET`. Tạo JWT secret Base64 từ 32 byte ngẫu nhiên bằng PowerShell:

   ```powershell
   $jwtBytes = New-Object byte[] 32
   $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
   $rng.GetBytes($jwtBytes)
   [Convert]::ToBase64String($jwtBytes)
   $rng.Dispose()
   ```

   Dán kết quả vào `JWT_SECRET`. Không commit `.env`. Compose tự đặt `DATABASE_URL` trỏ tới service `db`; giá trị `DATABASE_URL` trong `.env` vẫn dành cho chạy local.

2. Dừng backend đang dùng cổng 8080 rồi chạy:

   ```powershell
   docker compose config --quiet
   docker compose up -d --build
   docker compose ps
   docker compose logs --tail=100 backend
   ```

   Có thể đặt `BACKEND_PORT` trong `.env` nếu cổng 8080 đã được dùng. Dockerfile build bằng Java 21, chạy `mvn clean verify`, rồi đưa JAR vào image JRE chạy bằng user không phải root. Lần build đầu cần tải image và Maven dependencies.

3. Kiểm tra API (thay cổng nếu đã đổi `BACKEND_PORT`):

   ```powershell
   Invoke-RestMethod http://localhost:8080/actuator/health
   ```

   Kết quả mong đợi là `status: UP`. Mở `http://localhost:8080/swagger-ui.html` để thử đăng ký, đăng nhập và CRUD Workspace. Backend dùng profile `prod`, chờ PostgreSQL healthy và tự chạy Flyway; không cần tạo database hoặc chạy SQL bằng tay.

4. Dừng và khởi động lại để kiểm tra dữ liệu được giữ:

   ```powershell
   docker compose down
   docker compose up -d
   ```

   Named volume giữ dữ liệu PostgreSQL. `docker compose down -v` xóa volume và toàn bộ dữ liệu database của stack. Biến `POSTGRES_*` khởi tạo database khi volume trống; sửa mật khẩu trong `.env` không tự đổi mật khẩu của database đã có.

Frontend tiếp tục chạy bằng `npm ci` và `npm run dev` trong `frontend/`; Vite proxy `/api` tới cổng 8080. Nếu đổi cổng backend, chỉnh proxy trong `frontend/vite.config.js` cho tương ứng. Compose gồm backend, PostgreSQL và AI Service FastAPI tại cổng 8000. Có thể chạy riêng AI Service bằng `docker compose up -d --build ai-service`; xem [hướng dẫn AI Service](ai-service/README.md).

## Kiểm thử backend

Bộ kiểm thử dùng **JUnit và Mockito** cho unit test, cùng **Spring Boot và H2** cho integration test. Các integration test API tự khởi động backend trên cổng ngẫu nhiên, chạy Flyway và kiểm tra dữ liệu trên database in-memory riêng; không cần chạy PostgreSQL, backend hay frontend trước.

Chạy toàn bộ bộ test từ thư mục gốc:

```powershell
cd backend
.\mvnw.cmd clean test
```

Chạy riêng nhóm kiểm thử đăng ký và đăng nhập:

```powershell
.\mvnw.cmd "-Dtest=AuthServiceTest,AuthApiTest" test
```

Phạm vi kiểm thử hiện có:

- Đăng ký, đăng nhập, chuẩn hóa email, mã hóa mật khẩu, role mặc định và JWT.
- CRUD tài nguyên học tập, validation, phân trang và quyền sở hữu Workspace/Course.
- Câu hỏi, chấm Quiz, lịch sử làm bài, cập nhật confidence và rollback transaction khi có lỗi.
- Tiến độ, thống kê học tập, phiên học, thảo luận, bình luận và thông báo.
- Phân quyền Admin, định dạng lỗi, tài liệu OpenAPI và số lượng truy vấn ở các luồng quan trọng.

Kiểm chứng chạy thành công **139 test**, không có failure, error hoặc test bị bỏ qua; trong đó nhóm Auth có 13 test. Báo cáo từng lớp được tạo tại `backend/target/surefire-reports/`. Kết quả integration test này được xác minh trên H2 ở chế độ PostgreSQL; chưa thay thế việc kiểm thử trên PostgreSQL thật.

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
| Document | `POST /api/courses/{courseId}/documents`, `GET /api/documents`, `GET/PUT/DELETE /api/documents/{id}` | Metadata, upload PDF và dọn tệp theo Course |
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

`ai-service/` đã có FastAPI, health check `GET /health`, Swagger UI `/docs`, trích xuất PDF/OCR bằng Docling, chia đoạn và sinh vector E5 cho tài liệu/câu hỏi. Backend có hàng đợi ingestion bền vững, xử lý lại, công bố phiên bản trong một giao dịch và tìm kiếm ngữ nghĩa theo quyền Course. Document hỗ trợ upload PDF, lưu trên volume và dọn tệp/vector khi xóa tài nguyên.

Backend và frontend Quiz hỗ trợ CRUD Question, nộp bài, tính điểm, lưu QuizAttempt và cập nhật confidence của Concept trong cùng transaction. Câu trả lời ngắn (`SHORT_ANSWER`) được so khớp chính xác với đáp án mẫu, phân biệt chữ hoa/thường, dấu, khoảng trắng và xuống dòng; không gọi AI. Frontend giữ nguyên đáp án mẫu/câu trả lời và hướng dẫn người soạn ghi định dạng trong đề. Lịch sử cũ giữ kết quả đã lưu. Frontend cũng có lịch sử Quiz, thống kê học tập, tiến độ Course, phiên học, thảo luận/bình luận, thông báo và quản trị. RAG Chat, citation và giao diện AI chưa triển khai. Backend kiểm tra quyền Course trước khi đọc dữ liệu; secret, token và tệp tải lên không được đưa vào Git.
