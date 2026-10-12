# M4-T05: Đo index trên PostgreSQL

Ngày đo: 2026-10-12.

## Điều kiện đo

- PostgreSQL 18.6 local; 100.000 QuizAttempt và 100.000 StudySession.
- User seed ID 2; lịch sử lấy trang đầu, 10 bản ghi.
- Cập nhật thống kê bằng ANALYZE trước khi đo; cùng dữ liệu và tham số ở hai pha.
- Mỗi query: 2 lượt làm nóng cache và 5 lượt đo; báo cáo trung vị Execution Time.
- Dùng EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON); đây là thời gian trong database, không phải độ trễ HTTP.
- Baseline giữ index V11 (user_id, quiz_id, created_at) và các primary key.
- Pha sau tạo hai index ứng viên trong transaction rồi ROLLBACK.
- Các lượt đo đều có shared read = 0; kết quả phản ánh cache đã làm nóng.

## Kết quả

| Query | Trước (ms) | Sau (ms) | Giảm thời gian | Shared hit trước → sau |
|---|---:|---:|---:|---:|
| Lịch sử Quiz | 0.625 | 0.038 | 93.9% | 27 → 19 |
| Thống kê phiên học | 6.557 | 0.205 | 96.9% | 918 → 12 |
| Thống kê Quiz | 0.202 | 0.203 | -0.5% | 20 → 19 |
| Đếm lịch sử | 0.154 | 0.125 | 18.8% | 20 → 8 |

## Thay đổi execution plan

- Lịch sử Quiz: Sort + Hash Join + Bitmap Scan chuyển sang Nested Loop + Index Scan trên idx_quiz_attempts_user_created_id; không còn Sort.
- Thống kê phiên học: Seq Scan chuyển sang Index Scan trên idx_study_sessions_completed_user.
- Thống kê Quiz: vẫn dùng Bitmap Scan; không có cải thiện đáng kể trong phép đo này.
- Đếm lịch sử: chuyển sang Index Only Scan; chi tiết Heap Fetches nằm trong plan JSON.

## Index đã thử

```sql
CREATE INDEX idx_quiz_attempts_user_created_id
    ON public.quiz_attempts (user_id, created_at DESC, id DESC);

CREATE INDEX idx_study_sessions_completed_user
    ON public.study_sessions (user_id)
    WHERE end_time IS NOT NULL;
```

## Kiểm chứng và giới hạn

- Kết quả dữ liệu của cả bốn query giống nhau ở hai pha.
- Sau ROLLBACK benchmark, danh sách index trùng trạng thái trước khi đo. Sau đó hai index được đưa vào migration `backend/src/main/resources/db/vendor/postgresql/V14__optimize_common_queries.sql` và đã được Flyway áp dụng thành công trên PostgreSQL local. Kiểm tra khởi động xác nhận version hiện tại là 14, validate thành công và cả hai index có đúng định nghĩa.
- SQL dùng để đo ở queries.sql; dữ liệu tổng hợp và thời gian riêng lẻ ở summary.json.
- Các tệp before-*.json và after-*.json chứa đủ 7 plan mỗi query (2 lượt làm nóng, 5 lượt đo), tổng cộng 56 lượt.
- Query lịch sử dùng a.*, q.*; cần đối chiếu thêm SQL log để kiểm chứng đúng SQL Hibernate của phiên bản đang chạy.
- Bộ seed phân bố đều theo user và insert theo nhóm user; dữ liệu thực tế có thể khác.
- Chưa đo chi phí ghi, kích thước index, tải đồng thời, cold cache hoặc các trang sâu.
- Kết quả ủng hộ thêm cả hai index cho các luồng đã đo; index lịch sử cải thiện truy vấn trang đầu, index phiên học cải thiện aggregate theo user.
