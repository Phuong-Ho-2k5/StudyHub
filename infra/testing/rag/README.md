# Kiểm chứng RAG Chat và citation

API public: `POST /api/courses/{courseId}/ai/chat`, Bearer JWT, body `{"question":"Transaction bảo đảm điều gì?","topK":5}`.
Chỉ dùng các chunk từ phiên bản đã công bố và thuộc Course của owner. `course-rag-v2` trả citations từ evidence đã kiểm tra quote và context thực sự gửi LLM. Không lưu lịch sử. API nguồn/PDF và giới hạn provenance được mô tả trong [README backend](../../../backend/README.md).

## Hợp đồng và nguồn

Mock OpenAI trong `openai/` dựa trên tài liệu chính thức kiểm tra ngày 27/10/2026:

- [Responses và structured output](https://developers.openai.com/api/docs/guides/structured-outputs).
- [Đếm input token, gồm cả schema và framing](https://developers.openai.com/api/docs/guides/token-counting).
- [GPT-4.1 mini](https://developers.openai.com/api/docs/models/gpt-4.1-mini).

Orbit search lần đầu trả các endpoint không phù hợp, lần truy vấn tiếp bị HTTP 403; dùng hợp đồng OpenAI chính thức. Không tìm mock trong workspace bên ngoài vì đây là kiểm thử cục bộ theo hợp đồng công khai; không pull/push hoặc đổi workspace binding.

Mock kiểm tra Bearer auth giả, message roles, context metadata, JSON schema, `store=false`, `truncation=disabled` và output reserve. Answer được lấy từ chính context nhận được để phát hiện sai đường truyền nội dung. Counter cục bộ kiểm tra chỉ một lượt generation, kể cả khi bị rate limit ở bước generation. Token count trong mock là số tổng hợp để kiểm tra điều phối, không phải tokenizer OpenAI thật. Response model giả không chứng minh chất lượng câu trả lời.

## Chạy mô phỏng cục bộ

Từ repo root, chạy mỗi lệnh trong một terminal:

```powershell
postman simulation run infra/testing/rag/rag.sim.yaml --port 4927 --no-history --output ndjson
postman simulation run infra/testing/docling/extraction.sim.yaml --port 4918 --no-history --output ndjson
```

Lấy URL thực tế từ event `listening`. Từ `ai-service/`:

```powershell
$env:RAG_SIMULATION_URL='http://127.0.0.1:4927'
.\.venv\Scripts\python.exe -m pytest tests/test_rag_provider_simulation.py -q
```

Từ `backend/`:

```powershell
$env:AI_SIMULATION_URL='http://127.0.0.1:4918'
.\mvnw.cmd clean test
```

Các marker `[insufficient]`, `[budget]`, `[malformed]`, `[rate-limit]`, `[unavailable]`, `[html-error]`, `[truncated]`, `[timeout]` chỉ là điều khiển fixture trong simulation, không có ý nghĩa đặc biệt trong ứng dụng thật. Mock nội bộ AI hỗ trợ một tập con; xem test tương ứng.

## Pipeline với PostgreSQL và FastAPI thật

Chạy FastAPI từ `ai-service/` trong terminal riêng:

```powershell
$env:AI_SERVICE_TOKEN='simulation-token'
$env:LLM_API_KEY='simulation-key'
$env:LLM_MODEL='gpt-4.1-mini'
$env:LLM_BASE_URL='http://127.0.0.1:4927/openai/v1'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8027
```

Từ repo root:

```powershell
$env:AI_SIMULATION_URL='http://127.0.0.1:4918'
$env:RAG_PIPELINE_AI_URL='http://127.0.0.1:8027'
$env:RUN_RAG_COLLECTION='1'
.\infra\test-pgvector.ps1
```

`CourseChatPipelineIT` dùng Spring HTTP thật, PostgreSQL disposable schema, FastAPI thật và E5 local thật để embedding/retrieve. Chỉ OpenAI là simulation. E5 cần model cache hoặc tải lần đầu; AI Service không đọc database. Không chạy hai Maven command đồng thời trên cùng `backend/target` vì có thể xóa class của tiến trình đang test.

`DocumentSourceRepositoryIT` kiểm tra ownership/published SQL thật, nguồn còn đọc được khi rebuild pending/failed, ID cũ biến mất sau publish và race publish/delete với generation bị chặn bằng latch. `RUN_RAG_COLLECTION=1` chạy collection trên Spring server/fixture của pipeline: PDF thật tạo tạm, owner/foreign/empty Course và stale chunk có sẵn; environment có JWT tạm bị xóa sau run. Xem `.superpowers/collection-run.log` trong checkout chạy test; không commit log/token. Bỏ biến này nếu chỉ chạy JUnit pipeline.

## Collection và eval thật

`collection/` gồm success, mở đúng chunk/PDF, foreign/stale source, empty retrieval, blank question và foreign Course. Cung cấp biến `baseUrl`, `accessToken`, `courseId`, `emptyCourseId`, `foreignCourseId`, `question`, `topK`, `staleDocumentId`, `staleChunkId` từ môi trường cục bộ. Course chính cần evidence đủ trả ANSWERED và managed PDF; stale Document cùng Course vẫn tồn tại nhưng chunk ID đã bị thay bởi re-ingest thành công. Chat tự lưu citation vào collection variables cho các request mở nguồn tiếp theo. Course rỗng thuộc owner token; foreign Course thuộc owner khác.

```powershell
postman collection lint infra/testing/rag/collection
postman collection run infra/testing/rag/collection --environment <local-environment-file>
```

`eval-fixtures.json` gồm 6 câu có evidence, 3 câu thiếu evidence và 1 injection. Eval trả phí chỉ chạy khi chủ động đặt `RUN_RAG_LIVE_TESTS=1` cùng `LLM_API_KEY` hợp lệ, model và `LLM_BASE_URL=https://api.openai.com/v1` trong process environment. Từ `ai-service/`:

```powershell
$env:RUN_RAG_LIVE_TESTS='1'
.\.venv\Scripts\python.exe -m pytest tests/test_rag_eval.py -q -s
Remove-Item Env:RUN_RAG_LIVE_TESTS
```

Gate tự động kiểm tra từ khóa expected facts (ít nhất 5/6), 3/3 abstention và không xuất marker injection. Cần đọc câu trả lời bằng rubric để đánh giá đầy đủ unsupported claims; keyword gate không chứng minh correctness toàn diện. Output eval chỉ ghi ID fixture, status, model cấu hình, promptVersion, latency và kết quả gate; không dump credential hay raw tài liệu. Đặt `LLM_MODEL` thành snapshot nếu cần tái lập chính xác; alias có thể được provider đổi snapshot.

Live eval kiểm tra evidence hợp lệ. Simulation có `[fake-id]`, `[fake-quote]`, `[missing-evidence]`, `[duplicate-evidence]` để kiểm tra 502 qua HTTP thật; các marker chỉ là fixture. Test trimming dùng provider giả với token count có kiểm soát, bảo đảm evidence ngoài `included_contexts` bị từ chối. Thành công với simulation không chứng minh chất lượng citation của model thật.

Nhật ký simulation chứa input/output tổng hợp; chỉ giữ ở `logs/` đã ignore. Không đưa tài liệu thật vào simulation log.
