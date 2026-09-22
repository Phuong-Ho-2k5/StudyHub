# AI service

FastAPI service dành cho parse, clean, chunk, embedding, retrieval và gọi LLM. Backend là cổng xác thực và kiểm tra quyền trước khi gọi service này.

Các thư mục trong `app/` là ranh giới trách nhiệm dự kiến. Chưa chọn provider LLM, embedding model hoặc dependency cụ thể trong skeleton này.
