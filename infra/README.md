# Hạ tầng

Docker Compose hiện nằm tại `docker-compose.yml` ở thư mục gốc; Dockerfile backend nằm tại `backend/Dockerfile`. Chạy các lệnh Compose từ thư mục gốc để dùng đúng file `.env` và build context.

Compose khởi động backend Spring Boot, PostgreSQL 17 và AI Service FastAPI. Database dùng named volume `postgres_data`, không mở cổng ra máy host. Backend chờ database healthy rồi chạy Flyway bằng profile `prod`. AI Service chạy độc lập, health check tại `/health`, publish trên `127.0.0.1:8000`; xem [hướng dẫn AI Service](../ai-service/README.md).

Xem [hướng dẫn Docker](../README.md#chạy-bằng-docker) để cấu hình và kiểm tra. Thư mục này dành cho cấu hình hạ tầng bổ sung như pgvector ở milestone sau. Không lưu dữ liệu runtime hoặc secret trong repository.
