# AI service

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

OCR/bố cục không bảo đảm đọc đúng mọi tài liệu. Hình chưa được mô tả có cảnh báo `PAGE_n_PICTURE_DESCRIPTION_UNAVAILABLE`; trang không có chữ có `PAGE_n_NO_TEXT`. Mô tả hình nằm riêng ở `description`, đánh dấu `descriptionMethod=vision_model`, không trộn vào `page.text`. Pipeline giữ tham chiếu hình và tọa độ trong PDF; không cung cấp ảnh crop hay API download PDF. Chưa chunk, embedding hoặc lưu kết quả ingestion.

Chạy kiểm thử có model thật (cần tải model; CPU chậm hơn unit test):

```powershell
$env:RUN_DOCLING_TESTS = '1'
$env:RUN_DOCLING_VLM_TESTS = '1' # tùy chọn: thêm model mô tả hình
.\.venv\Scripts\python.exe -m pytest -q
Remove-Item Env:RUN_DOCLING_TESTS
Remove-Item Env:RUN_DOCLING_VLM_TESTS
```

Linux dùng `TEST_FONT_PATH` nếu không có DejaVuSans tại đường dẫn mặc định trong smoke test.

## Kết nối backend

Backend là cổng xác thực và kiểm tra quyền trước khi gọi AI Service; frontend tiếp tục gọi backend. HTTP client có connect timeout 5 giây và request timeout mặc định 240 giây (`AI_SERVICE_TIMEOUT_SECONDS`).

| Nơi backend chạy | Giá trị `AI_SERVICE_URL` khi tích hợp |
| --- | --- |
| Trực tiếp trên máy | `http://localhost:8000` |
| Cùng Docker Compose | `http://ai-service:8000` |

Compose đã truyền URL nội bộ và token cho backend. Khi chạy local, profile `local` đọc `.env` gốc. Không commit `.env` hoặc giá trị token.
