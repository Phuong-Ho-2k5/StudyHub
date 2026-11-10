# Sinh Quiz nháp có nguồn

> Generation POST hiện trả `202 {jobId,statusUrl}` + `Location`, thay cho response đồng bộ mô tả ở tài liệu lịch sử bên dưới. Poll `GET /api/ai/jobs/{jobId}` đến terminal rồi đọc batch/Quiz qua API nghiệp vụ. Lỗi provider sau accepted nằm trong `job.errorCode`; lỗi input/scope vẫn từ chối trước enqueue. Xem [hợp đồng async hiện hành](../ai-jobs/README.md#async-contract). Collection đã chuyển sang contract async.


Chức năng sinh Quiz tạo Quiz `AI_GENERATED / DRAFT` và lưu toàn bộ Question/AnswerOption cùng evidence trong một transaction. Backend không giữ transaction trong lúc gọi AI. Workflow duyệt từng câu → CONFIRMED → publish đã được triển khai; xem [hợp đồng review](../quiz-review/README.md). Giao diện duyệt sẽ được triển khai riêng.

## API

Yêu cầu Bearer JWT; mọi POST generate cần `Idempotency-Key` (1–100 ký tự ASCII hiển thị được, không khoảng trắng).

| Request | Kết quả |
| --- | --- |
| `POST /api/documents/{id}/ai/generate-quiz` | 202 + jobId; cùng key/payload trả lại cùng job |
| `POST /api/courses/{id}/ai/generate-quiz` | Cùng semantics, dùng nhiều Document được chọn trong Course |
| `GET /api/quizzes/{id}/ai-generation` | Metadata generation, coverage, evidence, sourceCurrent; không gọi AI |
| `GET /api/quizzes/{id}` và `GET /api/quizzes/{id}/questions` | Đọc Quiz/Question hiện tại qua API có sẵn |

Body Document:

```json
{"title":"Ôn tập chương 1","questionCount":5,"startChunkIndex":0,"chunkLimit":10}
```

Body Course:

```json
{"title":"Ôn tập môn học","questionCount":5,"documentIds":[11,12],"chunkLimit":20}
```

`title` trim Unicode, 1–200 ký tự; `questionCount` 1–10, mặc định 5; `chunkLimit` 1–20, mặc định 10. Document mode có `startChunkIndex` int32 không âm, mặc định 0. Course mode bắt buộc 1–5 ID dương khác nhau, chunkLimit ít nhất bằng số Document; không nhận startChunkIndex. Không nhận trường lạ, null, chuỗi/boolean/số lẻ thay cho integer.

V1 chỉ sinh MULTIPLE_CHOICE, 4 lựa chọn khác nhau, đúng 1 đáp án đúng; `referenceAnswer` và `conceptId` null. Không tạo Concept và không cập nhật tiến độ học. Request và output không được tự chọn owner/status/sourceType. AI draft không thể publish qua PUT Quiz hoặc submit khi chưa publish; Quiz MANUAL giữ nguyên hành vi.

Response generate có `{quiz,questions,generation}`. `generation` chứa `id`, `model`, `promptVersion=quiz-generation-v1`, `coverage:[{documentId,ingestionId,includedChunkIds}]`, `evidence:[{questionId,citations}]`, `sourceCurrent`. Evidence có quote nguyên văn, chunk/document ID, title/page snapshot, sourceUrl và fileUrl nếu có managed file. Backend dựng URL từ nguồn đã kiểm tra, không dùng URL do model sinh.

## Coverage, nguồn và retry

Chỉ chunk của ingestion INDEXED đã publish mới được dùng. Document mode đọc cửa sổ chunk từ startChunkIndex; Course mode sort Document ID tăng dần, lấy round-robin theo chunkIndex tăng dần, tối đa chunkLimit tổng cộng. Không âm thầm bỏ Document chưa ingest hoặc ngoài scope. Coverage là phần thực gửi model, **không bảo đảm bao phủ toàn bộ Course/tài liệu dài**. Vượt token budget trả 422 thay vì cắt ngầm.

Khi nguồn đổi version/xóa trong lúc sinh, backend từ chối lưu. GET metadata vẫn đọc được snapshot sau re-ingest hoặc xóa nguồn, với `sourceCurrent=false`; sourceUrl luôn kiểm tra quyền hiện hành và có thể không còn mở được. Bản publish cũ vẫn dùng được khi rebuild chưa publish.

Idempotency key unique theo User; fingerprint gồm mode/resource và body chuẩn hóa, kể cả default. Cùng key/payload trả **snapshot generation ban đầu**, kể cả Quiz đã sửa title/câu/đáp án sau đó; dùng GET Quiz/Question để đọc nội dung hiện tại. Metadata/evidence mô tả nội dung lúc AI sinh, không tự chứng thực chỉnh sửa sau này. Cùng key khác payload trả 409; Quiz đã xóa thì 409, dùng key mới nếu muốn sinh lại. Request thất bại không giữ key thành công. Hai request đồng thời có thể gọi model hai lần nhưng chỉ commit một Quiz; không hứa exactly-once chi phí LLM.

Quote đúng chỉ chứng minh có đoạn trích trong context; không chứng minh câu hỏi và đáp án đúng về ngữ nghĩa. Người dùng vẫn phải đối chiếu khi duyệt. Workflow duyệt Quiz sử dụng generation ID, question IDs, evidence/coverage và sourceCurrent cho review. Sửa nội dung phải có expectedRevision, hủy approval câu sửa và confirmation; CONFIRMED/publish chỉ qua workflow, không qua PUT Quiz. Replay generate và snapshot ban đầu giữ nguyên.

| HTTP | Ý nghĩa |
| --- | --- |
| 400 / 401 / 404 | Input/key không hợp lệ; chưa xác thực; resource ngoài scope/đã xóa |
| 409 | Chưa có nội dung publish, nguồn đổi trong lúc sinh hoặc key conflict |
| 422 | Cửa sổ ngoài tài liệu, thiếu nội dung để sinh đủ câu hoặc vượt token budget |
| 502 | AI trả sai schema/số câu/option/evidence |
| 503 / 504 | AI chưa cấu hình/unavailable/rate limit; hết deadline |

## Kiểm thử local

Không gọi provider thật hay push/pull workspace. Fixture là dữ liệu tổng hợp; marker như `[fake-quote]`, `[budget]`, `[insufficient]` chỉ có ý nghĩa trong simulation. Dùng Postman CLI >=1.70 và Docker cho PostgreSQL integration.

Terminal 1, từ root:

```powershell
postman simulation run infra/testing/quiz-generation/quiz-generation.sim.yaml --port 4940 --no-history --output ndjson
```

Kiểm tra event `listening` và URL thực. Terminal 2, từ ai-service (dùng Python venv đã cài requirements):

```powershell
$env:QUIZ_SIMULATION_URL='http://127.0.0.1:4940'
.\.venv\Scripts\python.exe -m pytest tests/test_quiz_provider_simulation.py -q
$env:AI_SERVICE_TOKEN='simulation-token'
$env:LLM_API_KEY='simulation-key'
$env:LLM_MODEL='gpt-4.1-mini'
$env:LLM_BASE_URL='http://127.0.0.1:4940/openai/v1'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8040
```

Terminal 3, từ root:

```powershell
$env:QUIZ_PIPELINE_AI_URL='http://127.0.0.1:8040'
$env:RUN_QUIZ_COLLECTION='1'
postman collection lint infra/testing/quiz-generation/collection
.\infra\test-pgvector.ps1
```

Script PostgreSQL tạo container disposable, đặt biến PGVECTOR_TEST_* cho Maven profile pgvector-it, chạy suite rồi xóa container. Mỗi IT dùng schema UUID riêng, không dùng DB ứng dụng. QuizGenerationPipelineIT tự truyền fixture JWT vào environment file tạm rồi xóa; collection log ở `.superpowers/quiz-collection.log` chỉ dùng local. Không commit token hoặc log.

Unit/API không cần Docker, chạy tuần tự các Maven process:

```powershell
# backend/
.\mvnw.cmd "-Dtest=QuizGeneration*Test" test
.\mvnw.cmd test
# ai-service/
.\.venv\Scripts\python.exe -m pytest -q
```

Test conditional bị skip khi thiếu QUIZ_SIMULATION_URL, QUIZ_PIPELINE_AI_URL hoặc PGVECTOR_TEST_URL không phải bằng chứng integration pass. Simulation HTTP kiểm tra transport, deadline, schema/evidence, rollback và không retry; token count là số synthetic, chưa đánh giá tokenizer hoặc chất lượng model thật. FastAPI OpenAPI được sinh từ app, Spring API được mô tả bằng annotations; collection lưu assertions chạy được thay vì làm nguồn schema thứ hai.
