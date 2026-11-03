# Duyệt và xuất bản Quiz AI

Backend quản lý `DRAFT → CONFIRMED → PUBLISHED`. Người dùng duyệt từng câu hiện tại rồi gọi confirm và publish riêng. Workflow không gọi AI. MANUAL giữ CRUD hiện có và không sử dụng CONFIRMED.

## Hợp đồng

Mọi endpoint yêu cầu Bearer JWT của owner Workspace. Không có quyền hoặc ghép question thuộc Quiz khác trả 404. Thiếu JWT trả 401. Input sai trả 400; sai trạng thái/revision hoặc chưa đủ câu duyệt trả 409.

| Method / path | Body | Kết quả |
| --- | --- | --- |
| GET `/api/quizzes/{id}/review` | — | Nội dung hiện tại, revision, approvals, evidence gốc |
| POST `/api/quizzes/{id}/questions/{questionId}/review` | `{"expectedRevision":0}` | Duyệt câu AI/DRAFT |
| POST `/api/quizzes/{id}/questions/{questionId}/unreview` | Như trên | Hủy duyệt, tăng revision, quay DRAFT |
| POST `/api/quizzes/{id}/confirm` | Như trên | CONFIRMED khi có ít nhất 1 câu và tất cả hợp lệ/đã duyệt |
| POST `/api/quizzes/{id}/publish` | Như trên | PUBLISHED sau CONFIRMED; lúc này mới submit được |
| POST `/api/quizzes/{id}/archive` | Như trên | ARCHIVED, không reopen trong v1 |

Mutation thành công trả 200 cùng review hiện tại. Body workflow chỉ nhận `expectedRevision`, số nguyên không âm, không nhận null/string/float/boolean hoặc field lạ. Duyệt, confirm, publish, archive lặp cùng revision và trạng thái phù hợp trả 200; giữ nguyên timestamp đã có. Hủy duyệt làm tăng revision nên retry bằng revision cũ trả 409.

Review response gồm `quiz`, `contentRevision`, `totalQuestions`, `reviewedQuestions`, `canConfirm`, `canPublish`, `confirmedBy`, `confirmedAt`, `publishedAt`, `sourceCurrent`, `questions`. Mỗi phần tử questions có DTO `question` hiện tại, `reviewedBy`, `reviewedAt`, `evidenceOrigin`, `editedSinceGeneration`, `citations`. Thời điểm UTC ISO-8601; các thông tin chưa có trả null. Người duyệt lấy từ JWT.

## Sửa nội dung và chống tab cũ

1. Đọc GET review và giữ `contentRevision`.
2. PUT Quiz hoặc POST/PUT Question AI phải thêm `expectedRevision` trong body. DELETE Question AI dùng query `?expectedRevision=...`.
3. PUT Quiz AI chỉ nhận `status` bằng trạng thái đang có; đổi trạng thái phải dùng workflow. MANUAL không cần revision và không được gán CONFIRMED.
4. Sửa/thêm/xóa câu hoặc sửa title làm tăng revision, xóa confirmation và quay DRAFT. Câu sửa mất approval; câu thêm chưa duyệt; câu khác giữ approval. Request cập nhật giống nội dung cũ vẫn được xử lý như một lần sửa và cần duyệt/xác nhận lại.
5. PUBLISHED/ARCHIVED khóa title/câu/đáp án/Concept. Xóa toàn bộ Quiz vẫn theo API có sẵn. ARCHIVED là trạng thái cuối.
6. Khi nhận 409, tải lại GET review trước khi quyết định thao tác tiếp. Không tự retry bằng revision mới khi người dùng chưa xem nội dung mới.

MCQ AI yêu cầu đúng 4 option khác nhau sau chuẩn hóa Unicode/case/trim, đúng 1 đáp án đúng, referenceAnswer null. Quy tắc MANUAL giữ nguyên. Concept gắn qua sửa Question phải cùng Course. Không thể xóa Concept đang được AI Quiz tham chiếu; bỏ liên kết khi Quiz còn editable hoặc xóa Quiz trước.

Mọi mutation cùng khóa parent Quiz trong transaction; không tải Question vào persistence context trước khi chờ khóa. GET review cũng lấy khóa parent để trả revision và tập câu nhất quán. Đây là đồng bộ của API ứng dụng; không coi chỉnh SQL trực tiếp là thao tác workflow hợp lệ.

## Nguồn và nội dung hiện tại

`ORIGINAL_GENERATION` chỉ evidence của bản AI sinh ban đầu, không chứng thực nội dung sửa. Câu không có snapshot, bao gồm câu thêm thủ công, trả `NONE`, citations rỗng, editedSinceGeneration=true. So sánh nội dung bỏ qua ID option được tạo lại khi sửa. `sourceCurrent=false` cảnh báo nguồn đã đổi/xóa, vẫn cho phép người dùng duyệt snapshot; không tự động cấm publish. Nếu không có generation metadata, sourceCurrent=null.

Không ghi lại generation snapshot/evidence hoặc thay đổi retry của chức năng sinh Quiz. Replay generate vẫn trả bản sinh ban đầu; UI phải lấy GET review/Quiz/Question để đọc nội dung hiện tại. Quote đúng không chứng minh câu hỏi hoặc đáp án đúng ngữ nghĩa.

## Kiểm thử

Trong backend, chạy Maven tuần tự:

```powershell
.\mvnw.cmd "-Dtest=QuizReview*Test,QuizGenerationGuardTest,QuizApiTest,QuestionApiTest,QuizSubmitApiTest" test
.\mvnw.cmd test
```

Từ root (Docker đang chạy, Postman CLI đã cài):

```powershell
postman collection lint infra/testing/quiz-review/collection
$env:RUN_QUIZ_REVIEW_COLLECTION='1'
.\infra\test-pgvector.ps1
Remove-Item Env:RUN_QUIZ_REVIEW_COLLECTION
```

Script tạo PostgreSQL disposable; IT dùng schema UUID. `QuizReviewMigrationIT` kiểm tra nâng cấp V25→V26, constraint và cascade. `QuizReviewConcurrencyIT` kiểm tra edit/approve/confirm/publish đồng thời và cập nhật đang đợi row-lock. `QuizReviewCollectionIT` chạy 16 request/assertion trên Spring API thật với fixture tổng hợp. Token/environment chỉ tạo tạm rồi xóa; log ở `.superpowers/sdd/2026-11-03-m7-t04-ai-quiz-review/collection.log`, không commit log/token.

Không cần FastAPI hoặc provider để kiểm thử workflow. Test bị skip khi thiếu PGVECTOR_TEST_URL/RUN_QUIZ_REVIEW_COLLECTION không được tính là pass integration.

## Bàn giao giao diện duyệt Quiz

Thêm màn hình review, hiển thị approval từng câu, cảnh báo nguồn stale/nội dung đã sửa và số câu đã duyệt; dùng canConfirm/canPublish để bật nút, vẫn xử lý 409 từ server. Đọc lại revision sau mỗi mutation nội dung/hủy duyệt. UI cũ chưa hỗ trợ CONFIRMED/revision và không thể dùng nút publish CRUD để xuất bản AI Quiz. Spring annotations/DTO là nguồn schema; collection là ví dụ chạy được và assertions.
