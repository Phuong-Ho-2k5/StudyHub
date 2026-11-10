# Concept suggestions có nguồn và duyệt trước khi lưu

> Generation POST hiện trả `202 {jobId,statusUrl}` + `Location`, thay cho response đồng bộ mô tả ở tài liệu lịch sử bên dưới. Poll `GET /api/ai/jobs/{jobId}` đến terminal rồi đọc batch/Quiz qua API nghiệp vụ. Lỗi provider sau accepted nằm trong `job.errorCode`; lỗi input/scope vẫn từ chối trước enqueue. Xem [hợp đồng async hiện hành](../ai-jobs/README.md#async-contract). Collection đã chuyển sang contract async.


Luồng: chọn Document đã ingest → generate bản nháp → mở citation đối chiếu → sửa tên/mô tả → confirm từng gợi ý. Chỉ confirm tạo Concept `NEW`, confidence `0`; generate và GET draft không thay đổi tiến độ học.

API public có prefix `/api/courses/{courseId}/documents/{documentId}/ai/concept-suggestions`, Bearer JWT:

| Request | Kết quả |
| --- | --- |
| `POST` prefix, `{"startChunkIndex":0,"chunkLimit":10,"maxSuggestions":5}` | 201: batch + suggestions PENDING + citations + coverage |
| `GET` prefix + `/{batchId}` | 200: bản nháp đã lưu và `sourceCurrent` |
| `POST` prefix + `/{batchId}/items/{suggestionId}/confirm`, `{"name":"Atomicity","description":"Mô tả đã duyệt"}` | 201: `{suggestionId,concept}`; cùng nội dung gửi lại trả 200 và cùng Concept |

Generate cho phép bỏ các trường để dùng default 0/10/5; body bắt buộc là object. Integer phải đúng kiểu JSON: start >=0 trong int32, chunkLimit 1–20, maxSuggestions 1–10. Không nhận null, chuỗi, bool, số lẻ hoặc trường lạ. Confirm chỉ nhận name/description dạng chuỗi không trắng, trim Unicode, tối đa 150/1000 ký tự. Client không gửi source, owner, confidence hoặc status.

Mỗi batch xử lý đúng một cửa sổ chunk thuộc bản ingestion đã publish. `coverage.includedChunkIds` là context thực gửi LLM; `nextChunkIndex` dùng cho lần kế tiếp, null khi hết. Không âm thầm cắt context: vượt token budget trả 422 để caller giảm chunkLimit. Tài liệu có bản publish cũ vẫn đọc được khi rebuild pending/failed.

Mỗi suggestion có 1–3 citation: chunk/document ID, title/index, pageStart/pageEnd (có thể null), quote nguyên văn, sourceUrl và fileUrl nếu có managed file. Backend xác nhận citation nằm trong context và source hiện hành. Quote hợp lệ không tự chứng minh toàn bộ mô tả đúng ngữ nghĩa; người dùng vẫn phải đối chiếu trước khi duyệt.

Các lỗi giao diện duyệt cần xử lý:

- 400: request không hợp lệ; 401: cần đăng nhập; 404: tài nguyên không tồn tại hoặc ngoài scope.
- 409: chưa có nội dung publish, nguồn đã đổi, trùng tên Concept trong Course, retry với nội dung khác hoặc Concept đã bị xóa. Draft stale vẫn đọc được với sourceCurrent=false; generate lại trước khi confirm item PENDING.
- 422: cửa sổ ngoài tài liệu hoặc token budget; giảm chunkLimit khi vượt budget.
- 502: AI output/schema/evidence lỗi; 503: AI chưa cấu hình/unavailable; 504: quá deadline. Không để lại draft một phần hoặc Concept khi upstream thất bại.

Xác nhận từng item giúp UI giữ được các item chưa chọn và hiển thị lỗi riêng. Sau confirm thành công, tải lại batch hoặc cập nhật item thành CONFIRMED và thêm Concept vào danh sách. Phạm vi hiện tại chưa có API liệt kê batch, reject item, bulk confirm, async job hoặc tự tạo prerequisite. Không tự gọi lại confirm với nội dung sửa mới; dùng CRUD Concept sau khi đã tạo.

## Chạy kiểm thử local

Provider simulation dựa trên adapter Responses và fixture RAG hiện có, chỉ dùng dữ liệu tổng hợp. Không gọi dịch vụ tính phí, không push/pull workspace. Từ repo root, terminal 1:

```powershell
postman simulation run infra/testing/concepts/concepts.sim.yaml --port 4930 --no-history --output ndjson
```

Lấy URL thực từ event `listening`; port cố định trong ví dụ. Terminal 2, từ `ai-service/`:

```powershell
$env:CONCEPT_SIMULATION_URL='http://127.0.0.1:4930'
.\.venv\Scripts\python.exe -m pytest tests/test_concept_provider_simulation.py -q
$env:AI_SERVICE_TOKEN='simulation-token'
$env:LLM_API_KEY='simulation-key'
$env:LLM_MODEL='gpt-4.1-mini'
$env:LLM_BASE_URL='http://127.0.0.1:4930/openai/v1'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8030
```

Terminal 3, từ root, có Docker daemon:

```powershell
$env:CONCEPT_PIPELINE_AI_URL='http://127.0.0.1:8030'
$env:RUN_CONCEPT_COLLECTION='1'
.\infra\test-pgvector.ps1
```

Script tạo PostgreSQL pgvector disposable, chạy Maven profile pgvector-it rồi xóa container. Mỗi IT dùng schema UUID riêng, không chạm DB ứng dụng. `ConceptSuggestionConcurrencyIT` kiểm tra confirm/retry/name collision và race publish/delete khi generation bị giữ bằng latch; không giữ transaction trong HTTP. `ConceptSuggestionPipelineIT` chạy Spring HTTP + PostgreSQL + FastAPI + provider adapter thật, chỉ OpenAI là simulation; seed chunk publish trực tiếp nên không cần tải embedding model. Chỉ bật `RUN_CONCEPT_COLLECTION=1` khi muốn chạy collection trên fixture của IT. Thiếu biến môi trường thì các test phụ thuộc tương ứng được skip, không phải đã kiểm chứng.

Kiểm thử unit/API không cần Docker:

```powershell
# ai-service/
.\.venv\Scripts\python.exe -m pytest -q
# backend/
.\mvnw.cmd "-Dtest=Concept*Test" test
# root
postman collection lint infra/testing/concepts/collection
```

`ConceptSuggestionApiSupport` có dependency HTTP giả nội bộ để chạy H2 tests độc lập; khi có CONCEPT_PIPELINE_AI_URL, các request generation của nhóm test này đi qua FastAPI thật. Tests PostgreSQL kiểm tra query nguồn/schema/migration và concurrency trên engine thật. Không chạy hai Maven process trên cùng backend/target.

Collection gồm generate, GET draft, mở nguồn, confirm, retry, invalid input, foreign scope và stale source. `ConceptSuggestionPipelineIT` cấp JWT fixture vào environment tạm, xóa sau khi chạy; nhật ký `.superpowers/concept-collection.log` chỉ dùng local. Không commit token hoặc log chứa tài liệu thật.

Markers `[empty]`, `[fake-id]`, `[fake-quote]`, `[budget]`, `[malformed]`, `[truncated]`, `[rate-limit]`, `[unavailable]`, `[timeout]` chỉ điều khiển mock. Counter `GET /openai/_stats` cho thấy budget không gọi generate, rate limit không tự retry. Token count là số tổng hợp, không phải tokenizer thật; simulation không đánh giá chất lượng khái niệm của LLM thật.
