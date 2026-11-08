# Phản hồi câu trả lời AI

`POST /api/ai/messages/{messageId}/feedback`, Bearer JWT:

```json
{"rating":"HELPFUL","note":"Giải thích dễ hiểu."}
```

- `rating` là JSON string chính xác `HELPFUL` hoặc `NOT_HELPFUL`.
- `note` tùy chọn, JSON string hoặc null; `String.strip()`, blank thành null, tối đa 2.000 UTF-16 code units sau strip. Giữ newline bên trong.
- Full replacement: bỏ `note` khi cập nhật sẽ xóa ghi chú cũ. Field lạ hoặc sai kiểu trả 400.
- Tạo mới 201; cập nhật/retry 200. Cùng payload giữ id/createdAt/updatedAt. Thay nội dung giữ id/createdAt; cập nhật updatedAt. Sửa đồng thời: last-write-wins, ghi nguyên payload trong transaction.
- Response: `id`, `messageId`, `rating`, `note`, `createdAt`, `updatedAt`. Không nhận userId/timestamp từ client.
- Chỉ ASSISTANT đã lưu, gồm ANSWERED và INSUFFICIENT_CONTEXT; USER của mình trả 400. Message thiếu/ngoài quyền trả 404 trước kiểm tra role; JWT thiếu/sai trả 401. Phải sở hữu cả Session và Workspace của Course.
- Một feedback/User/Message; DB unique/check/FK cascade. Không có DELETE feedback.
- Không gọi AI, đổi nội dung/citation/confidence hay session.updatedAt. Feedback không làm thay đổi thứ tự lịch sử.

GET `/api/courses/{courseId}/ai/chat-sessions/{sessionId}/messages` thêm `feedback` nullable vào mỗi message. USER hoặc chưa đánh giá trả null. Chỉ thêm một SELECT feedback theo IDs ASSISTANT của trang; trang toàn USER không query feedback.

Transaction mutation lấy scalar scope, pin User bằng PostgreSQL FOR KEY SHARE (H2 FOR UPDATE), khóa Course → Session → Message rồi recheck ownership và upsert. Khóa message ngăn xóa trực tiếp giữa recheck và insert. Pin User ngăn deadlock giữa FK insert và cascade User. Không gọi HTTP khi đang giữ khóa.

## Kiểm thử

Từ `backend/`, không cần provider:

```powershell
.\mvnw.cmd '-Dtest=AIFeedback*Test,ChatHistoryApiTest,ChatHistoryPersistenceTest' test
```

Từ repo root, PostgreSQL/pgvector disposable (Docker Desktop cần chạy):

```powershell
.\infra\testing\feedback\test-postgres.ps1
```

Script dùng tên container ngẫu nhiên, port loopback ngẫu nhiên, dọn container của chính nó và khôi phục biến môi trường. Có thể đặt `PGVECTOR_TEST_URL`, `PGVECTOR_TEST_USER`, `PGVECTOR_TEST_PASSWORD` tới DB test riêng rồi chạy từ backend:

```powershell
.\mvnw.cmd '-Dtest=AIFeedback*Test' '-Dit.test=AIFeedbackMigrationIT,AIFeedbackConcurrencyIT' -Ppgvector-it verify
```

IT tạo schema UUID, chỉ dọn schema đó. Kiểm tra V26→V27 bảo toàn chat/citation, constraints/cascade, hai lần tạo đầu {201,200}, hai update nguyên payload, retry, appendTurn song song và xóa message/session/course/user trước khóa hoặc sau recheck. Latch/barrier và PostgreSQL lock state đồng bộ race; không dùng sleep. Thiếu env thì IT skip, không tính là pass.

Collection v3 có 9 request/assertion, chạy Spring HTTP thật trên H2 disposable, không gọi provider hoặc publish workspace:

```powershell
postman collection lint infra/testing/feedback/collection
cd backend
$env:RUN_FEEDBACK_COLLECTION='1'
try { .\mvnw.cmd '-Dtest=AIFeedbackCollectionTest' test }
finally { Remove-Item Env:RUN_FEEDBACK_COLLECTION }
```

Environment/token tạm được xóa sau chạy; `--no-report-events` giữ kết quả local. Log cục bộ `logs/feedback/collection.log`. Test fixture kiểm tra DB count và không lưu feedback khi bị từ chối; kiểm tra `/v3/api-docs` thật có endpoint.

Từ `frontend/`:

```powershell
npm test
npm run lint
npm run build
npx playwright test e2e/ai-feedback.spec.js e2e/chat-history.spec.js e2e/course-ai.spec.js
```

UI fixtures chỉ chứng minh hành vi giao diện. Backend/PostgreSQL tests mới chứng minh ownership, persistence và locking.

## Thử bằng hai tài khoản

1. Tài khoản A mở Course → Trợ lý AI → Hỏi tài liệu, tạo Session và gửi câu hỏi để có ASSISTANT message đã lưu.
2. Chọn Hữu ích/Chưa hữu ích, có thể thêm ghi chú, bấm Gửi phản hồi. Chỉ sau save thành công mới hiện Đã lưu phản hồi. Tải lại lịch sử thấy đánh giá cũ; sửa rồi bấm Cập nhật phản hồi.
3. Dùng JWT tài khoản B gửi POST tới messageId của A: 404. Dùng JWT A gửi tới USER message của A: 400.
4. Khi save lỗi, giữ draft để thử lại hoặc tải lại lịch sử. Lưu feedback không chặn gửi câu hỏi. Đổi Session/Course bỏ response muộn của scope cũ.
