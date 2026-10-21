# Hạ tầng

Docker Compose hiện nằm tại `docker-compose.yml` ở thư mục gốc; Dockerfile backend nằm tại `backend/Dockerfile`. Chạy các lệnh Compose từ thư mục gốc để dùng đúng file `.env` và build context.

Compose khởi động backend Spring Boot và PostgreSQL 17. Database dùng named volume `postgres_data`, không mở cổng ra máy host. Backend chờ database healthy rồi chạy Flyway bằng profile `prod`.

Xem [hướng dẫn Docker](../README.md#chạy-bằng-docker) để cấu hình và kiểm tra. Thư mục này dành cho cấu hình hạ tầng bổ sung như pgvector ở milestone sau. Không lưu dữ liệu runtime hoặc secret trong repository.
