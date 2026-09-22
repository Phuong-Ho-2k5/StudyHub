# StudyHub

StudyHub là nền tảng quản lý học tập và trợ lý AI/RAG.

## Cấu trúc

```text
StudyHub/
├── backend/                 # Spring Boot: API, xác thực, quyền, nghiệp vụ
├── ai-service/              # FastAPI: ingestion, retrieval, generation
├── frontend/                # Ứng dụng React trong tương lai
├── infra/                   # Cấu hình hạ tầng và triển khai
├── docs/                    # Tài liệu kỹ thuật bổ sung
├── .env.example             # Tên biến môi trường cần cấu hình
├── .editorconfig
└── .gitignore
```

## Nguyên tắc kiến trúc

- Backend kiểm tra JWT và quyền truy cập Course trước mọi tác vụ nghiệp vụ hoặc AI.
- AI service xử lý tài liệu, embedding, retrieval và gọi LLM; kết quả AI được backend kiểm tra trước khi lưu thành dữ liệu nghiệp vụ.
- Retrieval luôn được giới hạn theo Course đã được cấp quyền; citation phải truy ngược được về Document/page/chunk.
- Không lưu secret, token, dữ liệu tải lên hoặc file môi trường vào Git.

## Bắt đầu

Backend đã có bộ khung Spring Boot cho M1-T01. Các API nghiệp vụ và phần AI sẽ được bổ sung theo các milestone trong file kế hoạch. M1 tiếp tục với User, Auth, Workspace và Course; sau đó mở rộng nội dung học tập, Quiz và AI/RAG.

Sao chép `.env.example` thành `.env` khi triển khai và điền giá trị riêng của môi trường. Không commit `.env`.
