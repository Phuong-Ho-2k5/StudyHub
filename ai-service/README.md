# AI service

FastAPI service dành cho parse, clean, chunk, embedding, retrieval và gọi LLM. Backend là cổng xác thực và kiểm tra quyền trước khi gọi service này.

Service cung cấp ứng dụng chạy độc lập, health check và tài liệu OpenAPI. Các thư mục ingestion, retrieval và generation là ranh giới trách nhiệm dự kiến cho các tính năng tiếp theo. Chưa cần database, API key, provider LLM hoặc embedding model để khởi động service.

## Chạy local trên Windows

Yêu cầu Python 3.10 trở lên. Chạy từ thư mục gốc StudyHub:

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
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

## Tích hợp backend về sau

Backend là cổng xác thực và kiểm tra quyền trước khi gọi AI Service; frontend tiếp tục gọi backend. Hiện chưa có HTTP client hoặc endpoint proxy trong Spring Boot.

| Nơi backend chạy | Giá trị `AI_SERVICE_URL` khi tích hợp |
| --- | --- |
| Trực tiếp trên máy | `http://localhost:8000` |
| Cùng Docker Compose | `http://ai-service:8000` |

Biến trong `.env` không tự được chuyển vào container backend; khi bổ sung client, cần khai báo `AI_SERVICE_URL` trong `backend.environment` và cấu hình Spring tương ứng.
