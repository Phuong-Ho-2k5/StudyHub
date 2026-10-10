# StudyHub Backend

Backend dùng Spring Boot 4.1.1, Java 21, Maven, PostgreSQL, Flyway và JWT. Các API hiện có gồm Auth, Workspace, Course, Document, Concept và Quiz. Xem [README gốc](../README.md) để chạy cả backend lẫn frontend.

## Cấu hình

Profile `local` là mặc định. Khi chạy từ thư mục `backend/`, ứng dụng đọc `../.env` nếu có. Các biến cần thiết:

| Biến | Mục đích |
| --- | --- |
| `DATABASE_URL` | JDBC URL, ví dụ `jdbc:postgresql://localhost:5432/studyhub` |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | Tài khoản PostgreSQL |
| `JWT_SECRET` | Khóa JWT dạng Base64, giải mã thành ít nhất 32 byte |
| `JWT_EXPIRATION` | Thời hạn token tính bằng mili giây; mặc định `900000` |

Profile `prod` yêu cầu các biến database được cung cấp qua môi trường và chọn bằng `SPRING_PROFILES_ACTIVE=prod`. Test tích hợp dùng H2 trong bộ nhớ. Flyway áp dụng các migration trong `src/main/resources/db/migration/`; JPA dùng `ddl-auto: validate`.

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
- `GET /api/courses/{courseId}/progress`: tổng hợp tiến độ từ confidence hiện tại của các Concept, theo quyền owner Course; chi tiết bên dưới.
- `GET/POST /api/courses/{courseId}/quizzes`, `GET/PUT/DELETE /api/quizzes/{id}`: CRUD metadata Quiz theo owner Course. Tạo Quiz với `{"title":"..."}`; cập nhật với `{"title":"...","status":"DRAFT|PUBLISHED|ARCHIVED"}`. Quiz tạo thủ công có `sourceType=MANUAL`.
- `GET/POST /api/quizzes/{quizId}/questions`, `GET/PUT/DELETE /api/questions/{id}`: CRUD Question và AnswerOption theo owner Quiz. Tạo và cập nhật Question bằng `{"text":"...","type":"MULTIPLE_CHOICE","options":[{"text":"A","correct":false},...]}`; `PUT` thay toàn bộ danh sách đáp án. `MULTIPLE_CHOICE` cần ít nhất 4 đáp án và ít nhất 1 đáp án đúng; `TRUE_FALSE` cần đúng 2 đáp án, 1 đáp án đúng. `SHORT_ANSWER` dùng `{"text":"...","type":"SHORT_ANSWER","referenceAnswer":"Đáp án mẫu cho AI","options":[]}`. API quản lý Question dành cho owner trả về đáp án đúng và đáp án mẫu; không dùng response này làm đề thi cho người học.
- `POST /api/quizzes/{quizId}/submit`: kiểm tra và chấm bài nộp của owner Quiz. Request: `{"answers":[{"questionId":1,"answerOptionIds":[2]},{"questionId":3,"answerText":"Lời giải"}]}`. Chỉ nhận Quiz `PUBLISHED`; mỗi câu phải được trả lời đúng một lần, option phải thuộc đúng câu, `SHORT_ANSWER` nhận văn bản tối đa 4000 ký tự và hiện chờ chấm (`isCorrect=null`). Response `200` trả `QuizResult` với số câu đã chấm/đúng/sai/chờ chấm, điểm phần trăm và kết quả từng câu. Điểm bằng số câu đúng chia tổng số câu, nhân 100.

Mỗi lần submit thành công tạo một bản ghi mới trong `quiz_attempts`, lưu Quiz, người dùng đăng nhập, các số đếm, điểm và thời điểm `created_at`. Làm lại không ghi đè lần trước; thay đổi Question sau đó không tính lại điểm đã lưu. Luồng submit chạy trong một transaction; bài nộp không hợp lệ hoặc không có quyền không tạo Attempt, lỗi lưu không trả thành công. Attempt hiện lưu kết quả tổng hợp; API lịch sử thuộc M4-T04. Xóa Quiz hoặc User sẽ xóa các Attempt liên quan theo khóa ngoại `ON DELETE CASCADE`.

Migration V10 tạo bảng Attempt; V11 đổi `question_percentage` sang `DOUBLE PRECISION` để giữ độ chính xác của response và thêm index `(user_id, quiz_id, created_at)`. Các migration được Flyway áp dụng khi khởi động.
- `GET /actuator/health`: health check.

Các endpoint nghiệp vụ yêu cầu `Authorization: Bearer <accessToken>`. `GET /api/courses` nhận `workspaceId`, `status`, `q`, `page`, `size`, `sort`; ví dụ:

```text
/api/courses?status=IN_PROGRESS&q=java&page=0&size=10&sort=name,asc
```

Test tích hợp trong `src/test/java/com/studyhub/` kiểm tra quyền truy cập và các luồng API hiện có.

## Tiến độ Course

`GET /api/courses/{courseId}/progress` yêu cầu JWT. Course không tồn tại hoặc thuộc người khác trả `404`; chưa xác thực trả `401`.

- `progressPercentage` = tổng confidence / tổng số Concept, tính cả Concept có confidence bằng 0.
- `masteredConcepts`: confidence từ 80 đến 100; `developingConcepts`: từ 50 đến 79; `needsReviewConcepts`: từ 0 đến 49. Phân nhóm theo confidence, độc lập với `Concept.status`.
- `masteryPercentage` = số Concept có confidence ≥ 80 / tổng số Concept × 100.
- Các phần trăm làm tròn đến 2 chữ số thập phân theo `HALF_UP`. Course chưa có Concept trả tất cả số đếm và phần trăm bằng 0.

Ví dụ Course có confidence `100, 80, 60, 40, 0`:

```json
{
  "courseId": 1,
  "totalConcepts": 5,
  "progressPercentage": 56.0,
  "masteredConcepts": 2,
  "developingConcepts": 1,
  "needsReviewConcepts": 2,
  "masteryPercentage": 40.0
}
```

API chỉ đọc và tính từ dữ liệu hiện tại, không lưu bảng tiến độ hay đổi `Course.status`. Thêm Concept hoặc giảm confidence có thể khiến tiến độ giảm. Quiz đã cập nhật confidence nên không cộng điểm Quiz lần nữa. `CourseProgressApiTest` kiểm tra công thức, ngưỡng, làm tròn, dữ liệu rỗng, thay đổi dữ liệu, phạm vi Course và quyền truy cập.

## Phiên học (M4-T02)

Hai API yêu cầu JWT và chỉ thao tác với dữ liệu của người dùng đăng nhập:

- `POST /api/study-sessions/start`, body `{"courseId": 1}`: kiểm tra quyền Course, tạo phiên học và trả `201`.
- `POST /api/study-sessions/{id}/finish`, không cần body: kết thúc phiên và trả `200`. Phiên không tồn tại hoặc thuộc người khác trả `404`; phiên đã kết thúc trả `409`.

Response gồm `id`, `userId`, `courseId`, `startTime`, `endTime`, `duration`. Thời gian do server ghi theo UTC; `duration` là số giây hoàn chỉnh giữa hai thời điểm. Khi đang học, `endTime` và `duration` là `null`. Mỗi lần start tạo một phiên mới.

Migration V13 tạo bảng `study_sessions`. Luồng finish chạy trong transaction và khóa bản ghi để hai request đồng thời chỉ kết thúc thành công một lần. `StudySessionApiTest` kiểm tra lưu dữ liệu, thời lượng, validation, JWT, quyền sở hữu và finish trùng/đồng thời.

## Thống kê người dùng (M4-T03)

`GET /api/users/me/statistics` yêu cầu `Authorization: Bearer <accessToken>` và trả `200` với thống kê của user trong JWT, tổng hợp trên tất cả Course. API không nhận userId để chọn người dùng; JWT thiếu hoặc không hợp lệ trả `401`.

```json
{
  "completedStudySessions": 2,
  "totalStudySeconds": 1800,
  "quizAttemptCount": 2,
  "averageQuizScore": 75.0
}
```

- `completedStudySessions`: số phiên đã kết thúc (`endTime` khác null), tính cả phiên có thời lượng 0.
- `totalStudySeconds`: tổng duration đã lưu của các phiên đã kết thúc, đơn vị giây. Phiên đang học chưa được tính.
- `quizAttemptCount`: số lần nộp Quiz đã lưu; làm lại cùng Quiz vẫn tính từng lần.
- `averageQuizScore`: trung bình cộng `questionPercentage` đã lưu của tất cả Attempt (0–100), làm tròn 2 chữ số thập phân theo `HALF_UP`. Mỗi Attempt có trọng số bằng nhau, không phụ thuộc số câu. Attempt có câu chờ chấm vẫn được tính theo điểm đã lưu; sửa Question không tính lại điểm lịch sử.

Khi không có phiên đã kết thúc hoặc không có Attempt, các chỉ số tương ứng bằng 0. Ví dụ trên dùng hai phiên 600/1200 giây và hai Attempt 50/100 điểm. API chỉ đọc, dùng hai query `COUNT/SUM` và `COUNT/AVG` riêng để tránh nhân bản dữ liệu khi join; không cần bảng hoặc migration mới.

`UserStatisticsApiTest` kiểm tra tổng hợp trên nhiều Course, làm Quiz nhiều lần, làm tròn, câu chờ chấm, phiên đang học, dữ liệu rỗng, thời lượng lớn, JWT và tách dữ liệu giữa các user.

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
