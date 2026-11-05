# Tư vấn học tập cá nhân hóa

`POST /api/courses/{courseId}/ai/learning-advice` nhận Bearer JWT và body:

```json
{"question":"Tôi nên ôn phần nào và vì sao?","topK":5}
```

Backend tự lấy User từ JWT, kiểm tra Course ownership, chụp dữ liệu học tập rồi retrieve chunk đã publish. Client không được gửi userId hoặc learning context. Question bắt buộc là string 1–2000 ký tự sau trim; topK là integer 1–10, mặc định 5. Field lạ, null hoặc sai kiểu trả 400.

Response có `courseId`, `question`, `status`, `answer`, `asOf`, `model`, `promptVersion`, `learningEvidence`, `citations`, `limitations`.

| Trạng thái | Ý nghĩa |
| --- | --- |
| `ANSWERED` | Có fact học tập và citation tài liệu đã kiểm tra |
| `LEARNING_DATA_ONLY` | Tư vấn dựa trên fact học tập, không có citation tài liệu |
| `INSUFFICIENT_CONTEXT` | Chưa đủ dữ liệu để cá nhân hóa; fact/citation rỗng |

Course chưa có Concept, Attempt hoặc phiên học hoàn tất trả insufficient ngay, model null, không gọi embedding/LLM. Có dữ liệu học nhưng retrieval rỗng vẫn gọi advice với contexts rỗng. Lỗi dependency không được biến thành fallback thành công.

Snapshot gồm progress toàn Course, 10 Concept confidence thấp nhất (tie theo ID), tối đa 20 cạnh prerequisite trực tiếp của selection, 5 attempts gần nhất theo thời gian/ID giảm dần và tổng số phiên/thời gian học đã hoàn tất trên toàn lịch sử. Truy vấn lọc cả user và Course. Phiên đang chạy không tính; duration là giây. Snapshot được đọc trong transaction ngắn, không giữ transaction khi chờ HTTP. `asOf` là thời điểm bắt đầu đọc snapshot.

`progressPercentage` là confidence trung bình, `masteryPercentage` là tỷ lệ Concept có confidence >=80. Attempt chỉ có điểm tổng hợp, không có lịch sử sai theo câu/Concept. Không sửa confidence, tạo session/Quiz hay lưu advice vào chat. Chưa có giao diện riêng.

Fact do backend tạo, LLM chỉ tham chiếu ID; backend trả nguyên fact từ snapshot. Citation tài liệu dùng quote nguyên văn và API nguồn hiện có; backend kiểm tra lại ownership, quote và nguồn đã publish sau generation. Prompt version là `learning-advice-v1`.

`limitations` phân biệt không có Concept/Attempt/phiên hoàn tất, selection Concept/prerequisite bị giới hạn và retrieval không trả nguồn. Retrieval rỗng không chứng minh Course không có tài liệu. Token budget giữ learning facts, bỏ nguyên chunk từ cuối; có thể bỏ hết chunk và trả learning-only. Vẫn quá budget khi không còn chunk thì 422.

Mã lỗi: 401 JWT thiếu/sai, 404 Course ngoài scope, 409 citation thay đổi, 422 token budget, 502 output/evidence sai, 503 dependency unavailable/chưa cấu hình/rate limit, 504 timeout. Không trả raw lỗi provider.

## Kiểm thử cục bộ

Các lệnh sau không gọi OpenAI thật. Simulation dùng lại cấu trúc Responses wire fixture trong `../rag/openai/`, thay schema bằng learning advice. Không tìm/pull mock workspace bên ngoài; CLI cục bộ không có `postman status`. Không thay workspace binding hay publish tài nguyên.

Terminal thứ nhất, từ repo root:

```powershell
postman simulation run infra/testing/advice/advice.sim.yaml --port 4935 --no-history --output ndjson
```

Terminal thứ hai, từ `ai-service/`:

```powershell
$env:AI_SERVICE_TOKEN='simulation-token'
$env:LLM_API_KEY='simulation-key'
$env:LLM_MODEL='gpt-4.1-mini'
$env:LLM_BASE_URL='http://127.0.0.1:4935/openai/v1'
$env:LLM_TIMEOUT_SECONDS='1'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8035
```

Thời gian chờ 1 giây dùng để chạy fixture timeout, không phải giá trị khuyến nghị cho provider thật. Embedding E5 cần model đã tải/cached; dùng cấu hình hiện có của ứng dụng.

Terminal thứ ba, từ `ai-service/`:

```powershell
$env:ADVICE_SIMULATION_URL='http://127.0.0.1:4935'
.\.venv\Scripts\python.exe -m pytest tests/test_advice_service.py tests/test_advice_schemas.py tests/test_advice_api.py tests/test_advice_provider_simulation.py -q
```

Backend unit/API dùng H2, không cần Docker. Từ `backend/`:

```powershell
.\mvnw.cmd '-Dtest=LearningContextRepositoryTest,LearningAdviceApiTest,LearningAdviceServiceTest,LearningAdviceClientTest' test
```

Pipeline dùng PostgreSQL/pgvector disposable, Spring và FastAPI/E5 thật, chỉ LLM là simulation. Cấu hình `PGVECTOR_TEST_URL`, `PGVECTOR_TEST_USER`, `PGVECTOR_TEST_PASSWORD` trỏ DB test riêng, quyền tạo/xóa schema; không dùng DB production. Test tự tạo schema UUID và dọn riêng schema đó. Từ `backend/`:

```powershell
$env:ADVICE_AI_URL='http://127.0.0.1:8035'
$env:RUN_ADVICE_COLLECTION='1'
.\mvnw.cmd '-Dtest=LearningContextRepositoryTest,LearningAdviceApiTest,LearningAdviceServiceTest,LearningAdviceClientTest' '-Dit.test=LearningAdvicePipelineIT' -Ppgvector-it verify
```

Có thể dùng container `pgvector/pgvector:0.8.7-pg17` như script `infra/test-pgvector.ps1` để cấp DB test. Test pipeline không được coi là pass nếu thiếu env và bị skip. `RUN_ADVICE_COLLECTION=1` bật collection trên fixtures tạo trong test; token tạm được xóa sau run. Log nằm trong `logs/advice-collection.log`, không commit log/token.

Collection bao gồm grounded, cold start, learning-only, foreign Course, invalid input, unavailable và forged fact. Kiểm tra cấu trúc:

```powershell
postman collection lint infra/testing/advice/collection
```

Các marker `[budget]`, `[malformed]`, `[unknown-fact]`, `[fake-id]`, `[fake-quote]`, `[unavailable]`, `[rate-limit]`, `[timeout]`, `[insufficient]`, `[learning-only]` chỉ điều khiển fixture. Code ứng dụng không có nhánh đặc biệt cho marker.

## Giới hạn bằng chứng kiểm thử

Simulation chứng minh serialization/auth, luồng fact, kiểm chứng nguồn, fallback, mapping lỗi và deadline qua HTTP. Token count là synthetic; câu trả lời fixture có tính quyết định, không chứng minh chất lượng lời khuyên từ model thật. Kiểm tra request events trong log simulation để xác nhận adapter đã gọi mock.

Trước khi dùng thực tế, đánh giá cùng một câu hỏi trên ba hồ sơ: Course chưa có hoạt động, confidence/điểm thấp, và người đã thành thạo. Lời khuyên phải phản ánh fact đúng, tránh suy diễn nguyên nhân hoặc gán câu sai từ Attempt tổng hợp; citation phải hỗ trợ nội dung kiến thức. Eval có provider thật cần được thực hiện riêng với dữ liệu phù hợp.
