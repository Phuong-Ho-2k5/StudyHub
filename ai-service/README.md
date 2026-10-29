# AI service

## RAG Chat và OpenAI

Backend gọi `POST /internal/rag/answer` với `X-AI-Service-Token`, `question` và 1–20 `contexts` đã kiểm tra quyền. FastAPI không retrieve hoặc truy vấn DB. Nội dung question và các context được chọn được gửi tới OpenAI; không gửi PDF đầy đủ hay credential nội bộ. Endpoint dùng Responses API với JSON schema, `store=false`, không tools, không streaming và không retry generation.

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `LLM_PROVIDER` | `openai` | Adapter duy nhất hiện hỗ trợ |
| `LLM_BASE_URL` | `https://api.openai.com/v1` | Chỉ cấu hình phía server; HTTP chỉ cho loopback simulation |
| `LLM_API_KEY` | trống | Key phía AI Service; không gửi cho backend/client |
| `LLM_MODEL` | `gpt-4.1-mini` | Model cấu hình, có thể đặt snapshot được tài khoản hỗ trợ |
| `LLM_TIMEOUT_SECONDS` | `60` | Deadline tổng đếm token + generation, tối đa 60 giây |
| `RAG_MAX_INPUT_TOKENS` | `8192` | Ngân sách input gồm system, question, metadata/context và schema |
| `LLM_MAX_OUTPUT_TOKENS` | `1024` | Token dành cho output |
| `LLM_CONTEXT_WINDOW_TOKENS` | `32768` | Trần ứng dụng, phải không vượt context window thật của model |

Đếm bằng `POST /responses/input_tokens` với cùng input và schema trước khi generation; nếu vượt budget, bỏ nguyên các chunk cuối theo thứ tự retrieval và đếm lại. Không chunk nào vừa trả 422/code `TOKEN_BUDGET_EXCEEDED`. Input/output budget phải vừa trần ứng dụng; không tự cắt question/context hoặc để provider truncate.

Request nội bộ tối đa 8 MiB; question 1–16384 ký tự sau trim; title 1–200 ký tự, content không trắng và tối đa 65536 ký tự/chunk. Page phải cùng null hoặc cùng có giá trị dương, end không trước start. Schema nghiêm ngặt, không nhận provider/model override từ caller.

`ANSWERED` hoặc `INSUFFICIENT_CONTEXT` cùng answer/model/promptVersion/evidence được backend kiểm tra lại. Internal contract `course-rag-v2` yêu cầu `evidence: [{chunkId, quote}]`: ID nguyên dương strict, quote không trắng 1–1000 ký tự Unicode, tối đa 20 chunk khác nhau. ANSWERED phải có evidence; INSUFFICIENT_CONTEXT phải có `[]`. Quote phải là substring nguyên văn của context thực sự đưa vào prompt sau trimming, không strip/normalize Unicode. Unknown/trimmed ID, quote sai, duplicate hoặc thiếu evidence đều trả 502. Backend tự map evidence sang citation, xác minh lại retrieval và published version; deploy hai service cùng v2.

Model alias được chuẩn hóa về tên cấu hình khi OpenAI trả snapshot có hậu tố ngày; pin snapshot khi cần tái lập. Thiếu cấu hình không làm hỏng `/health`; chat trả 503. Rate limit, provider auth lỗi hoặc 5xx trả 503; output sai/rỗng/refusal/truncated trả 502; deadline trả 504. Không trả raw provider error. Quote validation chứng minh provenance, không thay thế đánh giá correctness/grounding ngữ nghĩa.

Xem [hướng dẫn simulation, pipeline và eval](../infra/testing/rag/README.md). Hợp đồng được đối chiếu với [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs) và [Counting tokens](https://developers.openai.com/api/docs/guides/token-counting) ngày 27/10/2026.

FastAPI service dành cho parse, clean, chunk, embedding, retrieval và gọi LLM. Backend là cổng xác thực và kiểm tra quyền trước khi gọi service này.

Service cung cấp health check, OpenAPI và pipeline dùng Docling để đọc PDF, phân tích bố cục/bảng và OCR tiếng Việt/Anh bằng EasyOCR. Có thể bật mô tả hình bằng mô hình thị giác chạy local. Không cần database, provider LLM hoặc embedding model để khởi động service.

## Chạy local trên Windows

Yêu cầu Python 3.10 trở lên. Chạy từ thư mục gốc StudyHub:

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --env-file ../.env --reload --host 127.0.0.1 --port 8000
```

Lệnh dùng trực tiếp Python trong virtual environment, không cần activate hay đổi execution policy. `--reload` chỉ dành cho phát triển. Trên macOS/Linux, thay `.\.venv\Scripts\python.exe` bằng `.venv/bin/python`.

Mở Swagger UI tại <http://localhost:8000/docs>, OpenAPI JSON tại <http://localhost:8000/openapi.json>.

## Health check

`GET /health` không yêu cầu token, trả HTTP `200` và JSON:

```json
{"status": "UP", "service": "studyhub-ai"}
```

Đây là liveness check của ứng dụng HTTP, không khẳng định database/model/provider đã sẵn sàng. Khi thêm các dependency này, cần thiết kế readiness check riêng.

Kiểm tra trong terminal khác:

```powershell
Invoke-RestMethod http://localhost:8000/health
```

Chạy test từ `ai-service/`:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
```

## Docker Compose

Dừng Uvicorn local nếu đang dùng cổng 8000. Chuẩn bị `.env` của stack theo README gốc; Compose vẫn kiểm tra các biến backend/database dù chỉ khởi động AI Service. Chạy từ thư mục gốc StudyHub:

```powershell
docker compose config --quiet
docker compose up -d --build ai-service
docker compose ps ai-service
Invoke-RestMethod http://localhost:8000/health
```

Container dùng Python 3.12 và user không phải root, không dùng `--reload`, không phụ thuộc backend/database. Docker kiểm tra `/health` mỗi 10 giây. Cổng 8000 chỉ publish trên loopback của máy host.

```powershell
docker compose logs --tail=100 ai-service
docker compose stop ai-service
```

## Trích xuất Document

Frontend/client gọi backend:

```http
POST /api/documents/{documentId}/extract
Authorization: Bearer <JWT>
```

Backend kiểm tra owner, chỉ đọc tệp managed đã upload và gửi multipart sang `POST /internal/documents/extract`. API nội bộ nhận `documentId`, `file` và header `X-AI-Service-Token`. Không nhận URL hay đường dẫn tệp từ client. Thiếu cấu hình token trả 503, sai token trả 401 trước khi đọc multipart.

Đặt `AI_SERVICE_TOKEN` giống nhau ở hai service trong `.env`. Uvicorn local dùng `--env-file ../.env`; Compose truyền biến này cho cả hai container. Health vẫn chạy khi chưa cấu hình token/model. Kết quả gồm `documentId`, `source`, `pageCount`, `pages` và `warnings`; mỗi trang giữ `pageNumber`, `text`, `blocks` kèm `bbox` và `coordOrigin`.

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `AI_SERVICE_TOKEN` | trống | Token nội bộ, bắt buộc để trích xuất |
| `AI_MAX_FILE_BYTES` | `20971520` | Giới hạn PDF 20 MiB |
| `AI_MAX_PAGES` | `200` | Giới hạn số trang |
| `AI_EXTRACTION_TIMEOUT_SECONDS` | `180` | Deadline xử lý, kể cả tải model/mô tả hình |
| `AI_PICTURE_DESCRIPTIONS` | `false` | Bật mô tả hình/sơ đồ bằng local VLM |
| `AI_PICTURE_MODEL` | `HuggingFaceTB/SmolVLM-256M-Instruct` | Model mô tả hình |
| `AI_MODEL_CACHE_DIR` | thư mục `.cache` của user | Thư mục gốc chứa cache EasyOCR; cần quyền ghi |

Lần trích xuất đầu tải model công khai từ Hugging Face và model OCR; nội dung PDF được xử lý local. Cần mạng để tải lần đầu và đủ RAM cho PyTorch/Docling. Container dùng CPU PyTorch và cache ở named volume `ai_model_cache`; không tải model khi `/health` được gọi. Nếu lần đầu vượt deadline, có thể tải lại yêu cầu sau khi chuẩn bị cache. `docker compose down -v` xóa cache cùng các volume khác.

OCR/bố cục không bảo đảm đọc đúng mọi tài liệu. Hình chưa được mô tả có cảnh báo `PAGE_n_PICTURE_DESCRIPTION_UNAVAILABLE`; trang không có chữ có `PAGE_n_NO_TEXT`. Mô tả hình nằm riêng ở `description`, đánh dấu `descriptionMethod=vision_model`, không trộn vào `page.text`. Pipeline giữ tham chiếu hình và tọa độ trong PDF; không cung cấp ảnh crop hay API download PDF. Mặc định chỉ extract; bật chunking như mục Chunking bên dưới. Endpoint extraction không sinh embedding hoặc lưu kết quả; dùng ingestion ở mục Embedding và lưu chunk.

Chạy kiểm thử có model thật (cần tải model; CPU chậm hơn unit test):

```powershell
$env:RUN_DOCLING_TESTS = '1'
$env:RUN_DOCLING_VLM_TESTS = '1' # tùy chọn: thêm model mô tả hình
.\.venv\Scripts\python.exe -m pytest -q
Remove-Item Env:RUN_DOCLING_TESTS
Remove-Item Env:RUN_DOCLING_VLM_TESTS
```

Linux dùng `TEST_FONT_PATH` nếu không có DejaVuSans tại đường dẫn mặc định trong smoke test.

## Chunking

Gọi `POST /api/documents/{documentId}/extract?includeChunks=true` qua backend để nhận thêm `chunks`. API nội bộ dùng form `includeChunks=true`. Docling HybridChunker chạy trên cấu trúc tài liệu gốc, giữ heading context, nguồn trang/block/bbox và overlap có giới hạn token. Text OCR và mô tả hình sinh từ model vẫn được phân biệt.

Cấu hình AI: `AI_CHUNK_TOKENIZER_MODEL=intfloat/multilingual-e5-small`, `AI_CHUNK_MAX_TOKENS=384`, `AI_CHUNK_OVERLAP_TOKENS=48`. Tokenizer chỉ tải khi yêu cầu chunk; health và extraction-only không cần tokenizer. Tokenizer cần khớp embedding model.

Endpoint extraction không embedding, lưu chunk vào DB hoặc đổi trạng thái Document.

## Kết nối backend

Backend là cổng xác thực và kiểm tra quyền trước khi gọi AI Service; frontend tiếp tục gọi backend. HTTP client có connect timeout 5 giây và request timeout mặc định 240 giây (`AI_SERVICE_TIMEOUT_SECONDS`).

| Nơi backend chạy | Giá trị `AI_SERVICE_URL` khi tích hợp |
| --- | --- |
| Trực tiếp trên máy | `http://localhost:8000` |
| Cùng Docker Compose | `http://ai-service:8000` |

Compose đã truyền URL nội bộ và token cho backend. Khi chạy local, profile `local` đọc `.env` gốc. Không commit `.env` hoặc giá trị token.

## Embedding và lưu chunk

Upload PDF qua backend tự tạo tác vụ ingestion bền vững; worker đọc PDF managed sau khi kiểm tra owner, gọi extraction với chunking, rồi gửi các `embeddingText` theo batch tối đa 32 tới AI Service. Backend kiểm tra tất cả vector trước khi công bố một phiên bản hoàn chỉnh vào PostgreSQL/pgvector; xử lý lại thất bại giữ bản cũ. Client cũng có thể gọi `POST /api/documents/{documentId}/ingest` với JWT để chạy đồng bộ hoặc thử lại. AI Service không có credential database.

API nội bộ `POST /internal/embeddings` nhận header `X-AI-Service-Token` và JSON:

```json
{"texts": ["Chương 1\nNội dung đoạn tài liệu"]}
```

Kết quả gồm `model` (định danh `model@revision`), `dimensions` và `vectors` theo đúng thứ tự input. Tối đa 32 text, mỗi text không rỗng và tối đa 16.384 ký tự; text vượt 512 token sau khi thêm tiền tố và token đặc biệt bị từ chối bằng 422, không tự cắt. Adapter hiện hỗ trợ local `intfloat/multilingual-e5-small`, gộp vector có xét attention mask và chuẩn hóa L2.

Trường `inputType` nhận `passage` (mặc định, dùng cho tài liệu) hoặc `query` (dùng cho câu hỏi). Adapter tự thêm tiền tố `passage: ` hoặc `query: `; phía gọi gửi văn bản không có tiền tố. Loại đầu vào khác, kể cả null, bị từ chối bằng 422 trước khi nạp mô hình. Vector câu hỏi và tài liệu dùng cùng model/revision và số chiều:

```json
{"texts": ["Khi nào nên sử dụng transaction?"], "inputType": "query"}
```

Backend có `DocumentEmbeddingClient.embedQuery` để gọi chế độ câu hỏi, kiểm tra vector và giữ lỗi 422 khi vượt giới hạn token; `embed` cho tài liệu giữ nguyên hành vi. API `POST /api/courses/{courseId}/search` dùng vector câu hỏi để tìm các đoạn trong phiên bản chỉ mục đã công bố theo quyền Course; AI Service không truy vấn database hoặc quyết định phân quyền.

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `AI_EMBEDDING_MODEL` | `intfloat/multilingual-e5-small` | Model local; cần khớp `AI_CHUNK_TOKENIZER_MODEL` |
| `AI_EMBEDDING_REVISION` | `614241f622f53c4eeff9890bdc4f31cfecc418b3` | Commit SHA bất biến, dùng cho model và tokenizer của adapter |
| `AI_EMBEDDING_DIMENSIONS` | `384` | Kiểm tra dimension thực của model và từng vector |
| `AI_EMBEDDING_TIMEOUT_SECONDS` | `120` | Deadline mỗi batch, gồm cả lần tải/load model đầu tiên |

Backend cũng đọc model/revision/dimension để xác minh response. Thay cấu hình cần restart hai service; một lần ingestion không được trộn model hoặc dimension. Không cần API key cho model local. `EMBEDDING_MODEL` cũ chưa từng được sử dụng; dùng `AI_EMBEDDING_MODEL`.

Model chỉ tải khi gọi embedding lần đầu, được giữ trong một worker CPU và tái sử dụng giữa các batch. Worker bận trả 503, quá deadline trả 504 và bị dừng; lần gọi sau tạo worker mới. Model tải từ Hugging Face và cache cùng volume hiện có. Nếu lần tải đầu vượt deadline, chuẩn bị cache bằng smoke test hoặc tăng deadline phù hợp; `AI_SERVICE_TIMEOUT_SECONDS` của backend cần lớn hơn deadline AI. `/health` tiếp tục là liveness, không xác nhận model đã sẵn sàng.

Chạy test thường và smoke test có model thật, từ `ai-service/`:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
$env:RUN_EMBEDDING_TESTS = '1'
.\.venv\Scripts\python.exe -m pytest tests/test_embeddings.py -q
Remove-Item Env:RUN_EMBEDDING_TESTS
```

Smoke test cần mạng lần đầu và RAM cho model CPU. Xem [README backend](../backend/README.md) để kiểm tra dữ liệu pgvector và quy tắc ingest trùng.
