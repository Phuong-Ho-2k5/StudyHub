# StudyHub Backend

Backend dùng Spring Boot 4.1.1, Java 21, Maven, PostgreSQL, Flyway và JWT. Các API hiện có gồm Auth, Workspace, Course, Document, Concept, Quiz, DiscussionPost và Comment. Xem [README gốc](../README.md) để chạy cả backend lẫn frontend.

## Cấu hình

Tài liệu PDF hỗ trợ upload theo quyền Course, lưu tệp bền vững và dọn tệp khi xóa Document/Course/Workspace.

Profile `local` là mặc định. Khi chạy từ thư mục `backend/`, ứng dụng đọc `../.env` nếu có. Các biến cần thiết:

| Biến | Mục đích |
| --- | --- |
| `DATABASE_URL` | JDBC URL, ví dụ `jdbc:postgresql://localhost:5432/studyhub` |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | Tài khoản PostgreSQL |
| `JWT_SECRET` | Khóa JWT dạng Base64, giải mã thành ít nhất 32 byte |
| `JWT_EXPIRATION` | Thời hạn token tính bằng mili giây; mặc định `900000` |

Profile `prod` yêu cầu các biến database được cung cấp qua môi trường và chọn bằng `SPRING_PROFILES_ACTIVE=prod`. Flyway áp dụng các migration trong `src/main/resources/db/migration/`; JPA dùng `ddl-auto: validate`.

Flyway cũng đọc `db/vendor/{vendor}` theo database đang chạy. V14 nằm trong `db/vendor/postgresql/`, thêm index lịch sử Quiz `(user_id, created_at DESC, id DESC)` và partial index phiên học đã kết thúc `(user_id) WHERE end_time IS NOT NULL`. V14 được áp dụng khi backend khởi động với PostgreSQL. V15 tạo bảng `discussion_posts`, các khóa ngoại và index phục vụ danh sách bài theo Course. V16 tạo bảng `comments`, các khóa ngoại cascade và index phục vụ danh sách bình luận theo bài.

V18 trong `db/vendor/postgresql/` bật pgvector và tạo `document_chunks` để lưu nội dung, trang nguồn, model và embedding có kiểm tra số chiều. PostgreSQL phải cài pgvector trước khi chạy migration; Docker Compose đã có sẵn extension trong image. Xem [cấu hình và kiểm thử PostgreSQL thật](../infra/README.md#pgvector-và-documentchunk). Test H2 mặc định không chạy V18; dùng `.\infra\test-pgvector.ps1` từ gốc repo để chạy cả bộ backend và test vector.

## Chạy backend

Tạo database PostgreSQL và cấu hình `.env` ở gốc repo trước khi chạy local:

```powershell
.\mvnw.cmd spring-boot:run
```

Trên macOS/Linux, dùng `./mvnw`. `JAVA_HOME` cần trỏ tới thư mục JDK 21 trở lên.

## Tài liệu API tương tác

Sau khi backend chạy, mở [Swagger UI](http://localhost:8080/swagger-ui.html) để xem các nhóm API, request/response, quy tắc dữ liệu và mã lỗi. Swagger UI được phục vụ trực tiếp bởi backend, không cần chạy frontend.

- [OpenAPI JSON](http://localhost:8080/v3/api-docs)
- [OpenAPI YAML](http://localhost:8080/v3/api-docs.yaml)

Nếu đổi cổng backend, thay `8080` trong các đường dẫn tương ứng. Tài liệu được sinh từ controller và DTO đang chạy; có thể nhập URL OpenAPI vào Postman để tạo collection.

Để gọi API trên Swagger UI:

1. Mở `POST /api/auth/register`, chọn **Try it out** và đăng ký tài khoản nếu chưa có.
2. Gọi `POST /api/auth/login` với email và mật khẩu đã đăng ký.
3. Sao chép `accessToken` từ response, bấm **Authorize** và dán chỉ token, không thêm tiền tố `Bearer`.
4. Chọn API nghiệp vụ rồi bấm **Try it out → Execute**. Swagger UI tự thêm header `Authorization: Bearer <accessToken>`.

Đăng ký và đăng nhập không cần JWT. API `/api/admin/**` cần tài khoản có role `ADMIN`; tài khoản `USER` trả `403`. Các API tài nguyên vẫn kiểm tra ownership theo Workspace/Course, kể cả khi người gọi là admin. Token hết hạn trả `401`; đăng nhập lại và cập nhật token trong **Authorize**. Token không được lưu qua lần tải lại trang.

Danh sách phân trang dùng `page` bắt đầu từ 0, `size` và `sort=field,asc|desc`; có thể lặp `sort` để chọn nhiều trường. Giá trị mặc định của từng API được hiển thị trong tài liệu. Chức năng **Execute** gọi API thật và có thể tạo, sửa hoặc xóa dữ liệu.

## API

- `POST /api/auth/register`, `POST /api/auth/login`: đăng ký và nhận JWT.
- `GET/POST /api/workspaces`, `GET/PUT/DELETE /api/workspaces/{id}`: CRUD Workspace theo owner.
- `POST /api/workspaces/{workspaceId}/courses`, `GET /api/courses`, `GET/PUT/DELETE /api/courses/{id}`: CRUD Course theo owner Workspace.
- `GET /api/courses/{courseId}/progress`: tổng hợp tiến độ từ confidence hiện tại của các Concept, theo quyền owner Course; chi tiết bên dưới.
- `GET/POST /api/courses/{courseId}/discussion-posts`, `GET/PUT/DELETE /api/discussion-posts/{id}`: CRUD bài thảo luận theo quyền Course và tác giả; xem [quy tắc](#bài-thảo-luận).
- `GET/POST /api/discussion-posts/{postId}/comments`, `GET/PUT/DELETE /api/comments/{id}`: CRUD bình luận theo quyền Course và tác giả; xem [quy tắc](#bình-luận).
- `GET/POST /api/courses/{courseId}/quizzes`, `GET/PUT/DELETE /api/quizzes/{id}`: CRUD metadata Quiz theo owner Course. Tạo Quiz với `{"title":"..."}`; cập nhật với `{"title":"...","status":"DRAFT|PUBLISHED|ARCHIVED"}`. Quiz tạo thủ công có `sourceType=MANUAL`.
- `GET/POST /api/quizzes/{quizId}/questions`, `GET/PUT/DELETE /api/questions/{id}`: CRUD Question và AnswerOption theo owner Quiz. Tạo và cập nhật Question bằng `{"text":"...","type":"MULTIPLE_CHOICE","options":[{"text":"A","correct":false},...]}`; `PUT` thay toàn bộ danh sách đáp án. `MULTIPLE_CHOICE` cần ít nhất 4 đáp án và ít nhất 1 đáp án đúng; `TRUE_FALSE` cần đúng 2 đáp án, 1 đáp án đúng. `SHORT_ANSWER` dùng `{"text":"HTTP mặc định dùng cổng nào? Chỉ nhập số nguyên, không ghi đơn vị, không thêm khoảng trắng.","type":"SHORT_ANSWER","referenceAnswer":"80","options":[]}`. Đáp án mẫu phải có nội dung, tối đa 4000 ký tự và được lưu nguyên chuỗi. Người soạn ghi rõ định dạng trả lời trong `text`; chưa có trường hướng dẫn riêng. API quản lý Question dành cho owner trả về đáp án đúng và đáp án mẫu; không dùng response này làm đề thi cho người học.
- `POST /api/quizzes/{quizId}/submit`: kiểm tra và chấm bài nộp của owner Quiz. Request: `{"answers":[{"questionId":1,"answerOptionIds":[2]},{"questionId":3,"answerText":"80"}]}`. Chỉ nhận Quiz `PUBLISHED`; mỗi câu phải được trả lời đúng một lần, option phải thuộc đúng câu. `SHORT_ANSWER` chỉ nhận `answerText` có nội dung, tối đa 4000 ký tự và chấm ngay bằng `referenceAnswer.equals(answerText)`, trả `isCorrect=true/false`, không gọi AI. Phân biệt chữ hoa/thường, dấu, khoảng trắng và xuống dòng; không trim hoặc chuẩn hóa chuỗi. Response `200` trả `QuizResult` với số câu đã chấm/đúng/sai/chờ chấm, điểm phần trăm và kết quả từng câu. Với các loại câu hiện tại, bài nộp mới có `gradedCount=totalQuestions`, `pendingCount=0`. Điểm bằng số câu đúng chia tổng số câu, nhân 100. Câu trả lời ngắn gắn Concept cũng cập nhật confidence theo kết quả đúng/sai như các loại câu khác.

Frontend giữ nguyên `referenceAnswer` khi lưu và `answerText` khi nộp; chỉ kiểm tra chuỗi rỗng/toàn khoảng trắng và giới hạn độ dài. Nên dùng câu trả lời ngắn cho số, từ khóa hoặc cụm từ có đáp án duy nhất.

Mỗi lần submit thành công tạo một bản ghi mới trong `quiz_attempts`, lưu Quiz, người dùng đăng nhập, các số đếm, điểm và thời điểm `created_at`. Làm lại không ghi đè lần trước; thay đổi Question sau đó không tính lại điểm đã lưu. Luồng submit chạy trong một transaction; bài nộp không hợp lệ hoặc không có quyền không tạo Attempt, lỗi lưu không trả thành công. Attempt hiện lưu kết quả tổng hợp; xem API [lịch sử Quiz](#lịch-sử-quiz) bên dưới. Xóa Quiz hoặc User sẽ xóa các Attempt liên quan theo khóa ngoại `ON DELETE CASCADE`.

Migration V10 tạo bảng Attempt; V11 đổi `question_percentage` sang `DOUBLE PRECISION` để giữ độ chính xác của response và thêm index `(user_id, quiz_id, created_at)`. Các migration được Flyway áp dụng khi khởi động.
- `GET /actuator/health`: health check.

Các endpoint nghiệp vụ yêu cầu `Authorization: Bearer <accessToken>`. `GET /api/courses` nhận `workspaceId`, `status`, `q`, `page`, `size`, `sort`; ví dụ:

```text
/api/courses?status=IN_PROGRESS&q=java&page=0&size=10&sort=name,asc
```

## Bài thảo luận

Mỗi `DiscussionPost` thuộc một Course và một tác giả. Tất cả endpoint yêu cầu JWT và quyền truy cập Course thông qua owner Workspace hiện tại. Chưa có cơ chế thành viên Course dùng chung; API chưa mở quyền đọc/đăng bài cho user khác.

| Method | Endpoint | Thành công |
| --- | --- | --- |
| POST | `/api/courses/{courseId}/discussion-posts` | 201 + bài đã tạo |
| GET | `/api/courses/{courseId}/discussion-posts` | 200 + trang bài |
| GET | `/api/discussion-posts/{id}` | 200 + bài |
| PUT | `/api/discussion-posts/{id}` | 200 + bài đã sửa |
| DELETE | `/api/discussion-posts/{id}` | 204, không có body |

POST và PUT nhận cùng body; PUT thay cả tiêu đề và nội dung:

```json
{
  "title": "Dependency Injection",
  "content": "Constructor injection có lợi ích gì?"
}
```

`title` phải có nội dung và tối đa 200 ký tự; `content` phải có nội dung và tối đa 10.000 ký tự. Validation kiểm tra chuỗi đầu vào trước khi trim khoảng trắng ở hai đầu khi lưu, giữ nguyên khoảng trắng và xuống dòng bên trong. Request không có trường chọn tác giả hoặc Course; tác giả lấy từ user trong JWT, Course lấy từ URL tạo bài. PUT không đổi tác giả, Course hoặc `createdAt`.

Response gồm `id`, `courseId`, `authorId`, `title`, `content`, `createdAt`, `updatedAt`. Thời gian do server ghi. Không trả entity User/Course hoặc mật khẩu. GET danh sách nhận `page`, `size`, `sort`; mặc định `page=0`, `size=20`, `createdAt DESC, id DESC`. Ví dụ `/api/courses/1/discussion-posts?page=0&size=10&sort=id,asc`. Response dùng trang Spring Data có `content`, `totalElements`, `totalPages`, `number`, `size`. Course rỗng trả trang rỗng; Course không tồn tại trả 404.

Sửa/xóa kiểm tra thêm tác giả: quyền Course không thay thế quyền tác giả. JWT thiếu/sai trả 401; validation thất bại trả 400 với `fieldErrors`; Course/bài không tồn tại hoặc không có quyền trả 404 theo cấu trúc lỗi API chung. Xóa Course hoặc tác giả sẽ xóa bài liên quan theo khóa ngoại `ON DELETE CASCADE` của V15.

## Bình luận

Mỗi Comment thuộc một DiscussionPost và một tác giả. Tất cả endpoint yêu cầu JWT và quyền Course qua owner Workspace, giống bài thảo luận. Chưa có thành viên Course dùng chung hoặc trả lời bình luận nhiều cấp. Đường dẫn dùng `/discussion-posts` thống nhất với API bài thảo luận.

| Method | Endpoint | Thành công |
| --- | --- | --- |
| POST | `/api/discussion-posts/{postId}/comments` | 201 + bình luận đã tạo |
| GET | `/api/discussion-posts/{postId}/comments` | 200 + trang bình luận |
| GET | `/api/comments/{id}` | 200 + bình luận |
| PUT | `/api/comments/{id}` | 200 + bình luận đã sửa |
| DELETE | `/api/comments/{id}` | 204, không có body |

POST và PUT nhận `{"content":"Nội dung bình luận"}`. `content` bắt buộc, không được rỗng/toàn khoảng trắng, tối đa 10.000 ký tự trước khi trim hai đầu. Giữ nguyên khoảng trắng và xuống dòng bên trong. Tác giả lấy từ JWT, bài lấy từ URL khi tạo; body không dùng để chọn tác giả hoặc bài. PUT chỉ đổi nội dung và `updatedAt`, giữ nguyên `postId`, `authorId`, `createdAt`.

Response gồm `id`, `postId`, `authorId`, `content`, `createdAt`, `updatedAt`; không trả entity User/Post. Thời gian do server ghi. Danh sách nhận `page`, `size`, `sort`, mặc định `page=0`, `size=20`, `createdAt ASC, id ASC`. Ví dụ `/api/discussion-posts/1/comments?page=0&size=10&sort=id,desc`. Response là trang Spring Data với `content`, `totalElements`, `totalPages`, `number`, `size`. Bài hợp lệ chưa có bình luận trả trang rỗng; trang vượt cuối giữ tổng số bản ghi và trả `content: []`.

Mọi thao tác kiểm tra quyền Course; sửa/xóa kiểm tra thêm tác giả bình luận. Quyền Course hoặc quyền tác giả bài thảo luận không thay thế quyền tác giả bình luận. JWT thiếu/sai trả 401; validation trả 400 với `fieldErrors.content`; tài nguyên không tồn tại, không có quyền Course hoặc không phải tác giả khi sửa/xóa trả 404 theo cấu trúc lỗi chung.

Migration V16 tạo `comments`, index `(post_id, created_at, id)` và index `author_id`. Khóa ngoại `ON DELETE CASCADE` xóa bình luận khi xóa bài hoặc tác giả; xóa Course cũng xóa bình luận qua bài. Xóa bình luận không xóa bài hoặc bình luận khác.

## Tối ưu truy vấn

Danh sách Question dùng EntityGraph để tải `options` cùng câu hỏi; cả luồng xem câu hỏi và nộp Quiz dùng query này. Danh sách prerequisite tải `prerequisiteConcept` cùng dependency. Khi nộp Quiz, các Concept cần cập nhật confidence được lấy bằng một query `IN` có điều kiện owner Workspace; toàn bộ Concept phải hợp lệ và cùng Course trước khi cập nhật. Các thay đổi vẫn nằm trong transaction của submit.

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

API chỉ đọc và tính từ dữ liệu hiện tại, không lưu bảng tiến độ hay đổi `Course.status`. Thêm Concept hoặc giảm confidence có thể khiến tiến độ giảm. Quiz đã cập nhật confidence nên không cộng điểm Quiz lần nữa.

## Phiên học

Hai API yêu cầu JWT và chỉ thao tác với dữ liệu của người dùng đăng nhập:

- `POST /api/study-sessions/start`, body `{"courseId": 1}`: kiểm tra quyền Course, tạo phiên học và trả `201`.
- `POST /api/study-sessions/{id}/finish`, không cần body: kết thúc phiên và trả `200`. Phiên không tồn tại hoặc thuộc người khác trả `404`; phiên đã kết thúc trả `409`.

Response gồm `id`, `userId`, `courseId`, `startTime`, `endTime`, `duration`. Thời gian do server ghi theo UTC; `duration` là số giây hoàn chỉnh giữa hai thời điểm. Khi đang học, `endTime` và `duration` là `null`. Mỗi lần start tạo một phiên mới.

Migration V13 tạo bảng `study_sessions`. Luồng finish chạy trong transaction và khóa bản ghi để hai request đồng thời chỉ kết thúc thành công một lần.

## Thống kê người dùng

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

## Lịch sử Quiz

`GET /api/users/me/quiz-history` yêu cầu JWT và trả `200` với một trang lịch sử làm Quiz của user trong JWT trên tất cả Course. Không nhận userId để chọn người dùng; JWT thiếu hoặc không hợp lệ trả `401`.

```text
GET /api/users/me/quiz-history?page=0&size=10
```

- `page` bắt đầu từ 0; `size` mặc định là 10. Mặc định sắp xếp `createdAt DESC, id DESC`, lần nộp mới nhất trước; ID dùng để phân định khi trùng thời điểm.
- Có thể chọn thứ tự bằng `sort`, ví dụ `?sort=createdAt,asc&sort=id,asc`.
- Response là trang Spring Data có `content`, `totalElements`, `totalPages`, `number`, `size` và các thông tin phân trang khác. User chưa có Attempt trả `content: []`, `totalElements: 0`, `totalPages: 0`; trang vượt cuối trả danh sách rỗng và giữ tổng số bản ghi.

Ví dụ một phần tử trong `content`:

```json
{
  "attemptId": 1,
  "quizId": 2,
  "totalQuestions": 3,
  "gradedCount": 3,
  "correctCount": 1,
  "incorrectCount": 2,
  "pendingCount": 0,
  "questionPercentage": 33.333333333333336,
  "createdAt": "2026-10-11T01:00:00Z"
}
```

Mỗi lần submit được liệt kê riêng, kể cả làm lại cùng Quiz. Các số đếm và điểm lấy nguyên từ Attempt đã lưu, không làm tròn thêm hoặc tính lại khi Question thay đổi; `createdAt` trả theo UTC. Attempt cũ có câu chờ chấm giữ nguyên `pendingCount` và điểm đã lưu; thay đổi cách chấm chỉ áp dụng cho lần nộp mới. Lịch sử chỉ có kết quả tổng hợp, chưa lưu hoặc trả đáp án từng câu nên không có đủ dữ liệu để tự chấm lại những lần nộp cũ. Attempt bị xóa theo Quiz/User không còn xuất hiện.

API chỉ đọc, map entity sang DTO trong transaction và tải kèm Quiz bằng `EntityGraph`. Không cần bảng hoặc migration mới.

## Thông báo

Các API yêu cầu `Authorization: Bearer <accessToken>` và chỉ thao tác thông báo của người dùng trong JWT.

| Method | Endpoint | Kết quả |
| --- | --- | --- |
| GET | `/api/notifications?page=0&size=20` | `200`, danh sách phân trang |
| GET | `/api/notifications/unread-count` | `200`, số thông báo chưa đọc |
| PATCH | `/api/notifications/{id}/read` | `200`, thông báo sau khi đánh dấu đã đọc |

Danh sách mặc định sắp xếp `createdAt DESC, id DESC`. Response phân trang có `content`, `totalElements`, `totalPages`, `number`, `size`. Khi chưa có thông báo hoặc trang vượt cuối, `content` là danh sách rỗng. Có thể truyền `sort` để chọn thứ tự.

Ví dụ một phần tử trong `content`, cũng là response của thao tác đánh dấu đã đọc:

```json
{
  "id": 1,
  "type": "QUIZ_SUBMITTED",
  "content": "Bạn đã hoàn thành quiz #12, đạt 80%.",
  "read": true,
  "createdAt": "2026-10-14T06:00:00Z"
}
```

API đếm trả `{"unreadCount": 2}`. PATCH không cần body; gọi lại với thông báo đã đọc vẫn trả `200`. ID không tồn tại hoặc thuộc người khác trả `404`; JWT thiếu hoặc không hợp lệ trả `401`. User được xác định từ JWT, không nhận `userId` để chọn người nhận.

Mỗi lần nộp Quiz thành công tạo một thông báo `QUIZ_SUBMITTED` chưa đọc. Nội dung hiển thị điểm làm tròn tối đa hai chữ số thập phân; điểm Attempt giữ nguyên độ chính xác. Thông báo được lưu cùng transaction với Attempt và cập nhật confidence: nếu một bước thất bại, toàn bộ thay đổi rollback. Khi xóa người nhận, thông báo được xóa theo. Nội dung thông báo đã lưu được giữ nguyên khi Quiz thay đổi hoặc bị xóa.

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
| 409 | `CONFLICT` | Email đã tồn tại hoặc admin tự hạ quyền |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Content-Type không được hỗ trợ |
| 5xx | `INTERNAL_ERROR` | Lỗi server ngoài dự kiến, `message` không lộ chi tiết nội bộ |

Các HTTP status khác có mã `HTTP_<status>`. Response không trả token, stack trace hay giá trị input bị từ chối. `/actuator/health` tiếp tục dùng response của Actuator.

## API quản trị

Tất cả `/api/admin/**` yêu cầu JWT hợp lệ và role `ADMIN`. Thiếu/sai/hết hạn token trả `401 UNAUTHORIZED`; tài khoản `USER` trả `403 FORBIDDEN`, kể cả khi tài nguyên đích không tồn tại. API đăng ký luôn tạo `USER`, không nhận quyền quản trị từ body.

| Method | Endpoint | Kết quả |
| --- | --- | --- |
| GET | `/api/admin/users?page=0&size=20` | Trang người dùng toàn hệ thống; mặc định `id ASC`, size 20 |
| GET | `/api/admin/users/{id}` | Chi tiết người dùng, không tồn tại trả `404 NOT_FOUND` |
| PATCH | `/api/admin/users/{id}/role` | Đổi role; trả DTO người dùng sau cập nhật |
| GET | `/api/admin/statistics` | Tổng số người dùng, Workspace và Course toàn hệ thống |

Các endpoint thành công trả `200`. DTO người dùng chỉ gồm `id`, `email`, `name`, `role`, không chứa password/hash hoặc entity liên quan. Danh sách có `content`, `totalElements`, `totalPages`, `size`, `number`; `page` bắt đầu từ 0 và trang vượt cuối có `content: []`.

Body đổi role:

```json
{ "role": "ADMIN" }
```

Chỉ nhận chuỗi `USER` hoặc `ADMIN`, phân biệt hoa/thường; thiếu/null/sai role hoặc JSON lỗi trả `400`. Dùng chuỗi được validate để không chấp nhận số thứ tự enum. Đổi lại cùng role vẫn thành công. Admin tự đổi role thành `USER` trả `409 CONFLICT`; admin có thể đổi role của tài khoản khác. Không có user đích trả `404`.

Ví dụ response người dùng:

```json
{ "id": 12, "email": "learner@example.com", "name": "Learner", "role": "ADMIN" }
```

Ví dụ thống kê:

```json
{ "totalUsers": 10, "totalWorkspaces": 15, "totalCourses": 32 }
```

JWT filter tải lại tài khoản từ database mỗi request: nâng/hạ quyền có hiệu lực với request kế tiếp ngay cả khi dùng token đã cấp trước đó. Role `ADMIN` cho phép các API quản trị trên; các API Workspace/Course hiện có vẫn kiểm tra ownership theo quy tắc của chúng.

Để tạo admin đầu tiên trên database local, đăng ký tài khoản rồi chạy SQL với đúng email:

```sql
UPDATE users
SET role = 'ADMIN', updated_at = CURRENT_TIMESTAMP
WHERE email = 'admin@example.com';
```

Sau đó đăng nhập bằng tài khoản này. Không có endpoint đăng ký admin công khai, không có mật khẩu admin mặc định và không cần migration mới.
