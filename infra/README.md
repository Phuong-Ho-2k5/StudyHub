# Hạ tầng

M6-T02A–E bổ sung volume `document_files` cho backend và [CI ba thành phần](../.github/workflows/ci.yml).

Docker Compose hiện nằm tại `docker-compose.yml` ở thư mục gốc; Dockerfile backend nằm tại `backend/Dockerfile`. Chạy các lệnh Compose từ thư mục gốc để dùng đúng file `.env` và build context.

Compose khởi động backend Spring Boot, PostgreSQL 17 có pgvector (`pgvector/pgvector:0.8.7-pg17`) và AI Service FastAPI. Database dùng named volume `postgres_data`, không mở cổng ra máy host. Backend chờ database healthy rồi chạy Flyway bằng profile `prod`. AI Service chạy độc lập, health check tại `/health`, publish trên `127.0.0.1:8000`; xem [hướng dẫn AI Service](../ai-service/README.md).

Xem [hướng dẫn Docker](../README.md#chạy-bằng-docker) để cấu hình và kiểm tra. Không lưu dữ liệu runtime hoặc secret trong repository.

## M6-T02: pgvector và DocumentChunk

Migration PostgreSQL `V18__create_document_chunks.sql` bật extension `vector` và tạo bảng `document_chunks`. Flyway đã đọc `db/vendor/{vendor}` nên migration này không chạy trên H2. Không sửa migration cũ; Hibernate tiếp tục dùng `ddl-auto: validate`.

Từ thư mục gốc, áp dụng vào stack đã cấu hình `.env`:

```powershell
docker compose config --quiet
docker compose up -d --build db backend
docker compose logs --tail=100 backend
```

Image mới giữ PostgreSQL major 17 và đường dẫn volume hiện tại. Sao lưu database trước khi cập nhật môi trường có dữ liệu quan trọng. Không dùng `down -v` để cập nhật image. Với PostgreSQL cài trực tiếp, cài pgvector tương thích trước khi khởi động backend; tài khoản chạy Flyway cần quyền tạo extension, hoặc quản trị viên bật `CREATE EXTENSION IF NOT EXISTS vector` trong database trước.

Kiểm tra extension và bảng trong Compose (thay user/database nếu đã đổi trong `.env`):

```powershell
docker compose exec db psql -U studyhub -d studyhub -c 'TABLE pg_extension;'
docker compose exec db psql -U studyhub -d studyhub -c '\d document_chunks'
```

Mỗi chunk có nội dung, thứ tự từ 0, trang nguồn từ 1 (hoặc cả hai trang NULL), model, số chiều và vector. Khóa `(document_id, chunk_index)` chống trùng; xóa Document/Course/Workspace dọn chunk qua cascade. Course và owner được suy ra qua quan hệ hiện có, không sao chép vào chunk.

M6-T01B chưa chốt model/dimension. Vì vậy `embedding` dùng kiểu `vector` không cố định số chiều, với CHECK `vector_dims(embedding) = embedding_dimensions`. Mỗi bản ghi bắt buộc có model và số chiều dương. Ràng buộc này kiểm tra từng bản ghi, không bảo đảm các bản ghi cùng model có cùng số chiều. M6-T05/M6-T06 phải validate contract provider và lọc đúng model/dimension trước khi tính khoảng cách, đồng thời kiểm tra quyền Course. Không so sánh embedding khác model dù số chiều giống nhau.

Chưa tạo ANN index: cột vector có thể dùng exact search; sau khi chốt model/dimension mới thêm expression/partial index phù hợp. Sinh embedding, ingestion/versioning và API search thuộc các task tiếp theo.

## Kiểm thử PostgreSQL thật

Yêu cầu Docker đang chạy và JDK 21 trở lên. Từ thư mục gốc:

```powershell
.\infra\test-pgvector.ps1
```

Script tạo container riêng với cổng loopback ngẫu nhiên, chạy toàn bộ test backend và nhóm `PgvectorMigrationIT`, rồi dọn container cùng volume tạm kể cả khi test lỗi. Không dùng `.env` hoặc database ứng dụng. Báo cáo nằm trong `backend/target/surefire-reports` và `backend/target/failsafe-reports`.

Nhóm PostgreSQL kiểm tra upgrade V17 → V18 giữ dữ liệu cũ, chạy lại migration, lưu/đọc và tìm vector gần nhất, sai dimension/metadata, chunk trùng, khóa ngoại và xóa cascade. Bộ H2 mặc định không kiểm tra tính năng vector.

Có thể chạy trực tiếp với database PostgreSQL dùng riêng cho test:

```powershell
$env:PGVECTOR_TEST_URL = 'jdbc:postgresql://localhost:5432/studyhub_test'
$env:PGVECTOR_TEST_USER = 'studyhub_test'
$env:PGVECTOR_TEST_PASSWORD = '<mật khẩu test>'
cd backend
.\mvnw.cmd -Ppgvector-it verify
```

Test tạo schema ngẫu nhiên và xóa schema đó sau khi chạy; chỉ dùng database test vì extension được tạo ở cấp database. Tham khảo [tài liệu pgvector chính thức](https://github.com/pgvector/pgvector) về vector không cố định số chiều và index theo model.
