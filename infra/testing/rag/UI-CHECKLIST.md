# Kiểm tra giao diện Course AI

## Sử dụng

1. Chạy backend, FastAPI và frontend theo README gốc; chọn Course từ bảng học tập.
2. Trong **Tài liệu**, tải PDF. Upload tự tạo tác vụ; chờ “Đã có bản dùng cho AI”. Theo dõi trạng thái hoặc dùng “Cập nhật trạng thái” khi polling gặp lỗi.
3. Trong **Trợ lý AI**, chọn **Tìm trong tài liệu**, nhập truy vấn và tìm kiếm. **Xem nguồn** đọc lại đúng chunk đang được công bố.
4. Chọn **Hỏi tài liệu**, gửi câu hỏi. Mỗi lần hỏi độc lập, chỉ cặp câu hỏi/câu trả lời gần nhất được giữ trong bộ nhớ giao diện; không có lịch sử hoặc streaming.
5. Mở citation để đối chiếu quote nguyên văn. Chọn **Xem PDF** để tải bằng JWT và mở trang nguồn. PDF.js và worker được bundle cùng ứng dụng, không dùng CDN. Có nút trang trước/sau và link mở tệp trong tab mới.
6. Sau re-ingest thành công, nguồn cũ có thể trả 409: tìm hoặc hỏi lại. Rebuild lỗi vẫn giữ bản đã công bố. Không đặt JWT vào URL.

## Kiểm thử frontend

Từ `frontend/`:

```powershell
npm ci
npx playwright install chromium
npm test
npm run lint
npm run build
npm run test:e2e -- --workers=2
```

Playwright tự chạy Vite riêng trên `127.0.0.1:4179`; cần cổng trống. Các test intercept **chỉ** pathname bắt đầu `/api/`; không cấu hình `VITE_API_URL` trỏ backend ngoài khi chạy bộ fixture này. Không cần tài khoản thật hoặc API key. JWT fixture có hạn một giờ và chỉ dùng trong test. Trace khi thất bại cùng ảnh desktop/mobile nằm trong `frontend/test-results/` đã ignore.

### Phạm vi đã kiểm tra ngày 30/10/2026

Kết quả: **31 Node tests**, **23 Playwright Chromium tests**, lint và production build đều đạt.

- Node: route/payload/JWT, không retry write, PDF MIME/binary/error/abort, unauthorized đúng token, nullable page và bản published khi rebuild FAILED, input limit và schema chat.
- Playwright Chromium: search/chat, empty/422/503/504, malformed evidence, HTML được render như text, stale source 409, quote highlight, nullable PDF/trang, double-submit chat/ingest, upload không POST ingest thêm.
- Race: response search/chat từ Course cũ, logout trong chat, nguồn A về muộn sau B, đóng viewer trước PDF trả về, poll hai tài liệu với request thứ hai chậm, chuyển tab/phân trang khi poll đang chờ.
- Polling: dừng ở INDEXED, ẩn/hiện tab, dừng khi lỗi và retry thủ công. Cleanup abort GET đang chạy; chỉ publish state sau khi hoàn tất batch.
- PDF fixture ba trang màu khác nhau: canvas trang 2 thực sự có pixel xanh lá, điều hướng trang 3; Authorization qua header; Blob URL được revoke khi đóng. Trang không biết không được suy ra từ chunkIndex.
- Modal: Escape, trả focus về nút nguồn, Tab/Shift+Tab không thoát modal; mobile 390×844 không tràn ngang. Đã xem ảnh desktop/mobile do test sinh.

Đây là kiểm thử UI với public API response được điều khiển bằng Playwright route, không chứng minh hệ thống retrieve/generate thật hoạt động.

## Kiểm thử hợp đồng và simulation

Đã chạy:

- `postman collection lint infra/testing/rag/collection`: 8 request, 0 lỗi, 0 cảnh báo.
- Backend: `AI_SIMULATION_URL=http://127.0.0.1:4918` cùng `mvnw.cmd "-Dtest=CourseChatApiTest,DocumentSourceApiTest,CourseSearchApiTest,DocumentIngestionApiTest,OpenApiApiTest,CourseChatSimulationTest" test`: 14 test đạt. Spring test profile/H2 và simulation; không phải PostgreSQL pipeline.
- AI Service: `RAG_SIMULATION_URL=http://127.0.0.1:4927` cùng `.venv/Scripts/python.exe -m pytest tests/test_rag_provider_simulation.py -q`: 15 test đạt. Request log simulation xác nhận gọi token counting/generation và các failure case; không gọi OpenAI thật.

Mô tả cách bật simulation ở [README pipeline](README.md). Log chỉ giữ cục bộ trong `logs/` đã ignore, không commit token hoặc nội dung tài liệu thật.

## Phần còn phải xác minh trên môi trường tích hợp

- [ ] Upload → worker/Docling → E5 → PostgreSQL/pgvector → search/chat → citation/PDF qua UI với backend và AI Service thật; chỉ provider OpenAI dùng simulation.
- [ ] Chạy collection lên pipeline thật và xem PDF có provenance từ Docling, không chỉ fixture.
- [ ] Kiểm tra trình duyệt mục tiêu khác Chromium nếu cần triển khai hỗ trợ chúng.

Lượt triển khai này không chạy được pipeline PostgreSQL: Docker daemon không sẵn sàng; PostgreSQL 18 local kết nối được nhưng không có extension pgvector khả dụng. Không cài extension hoặc sửa database ứng dụng để vượt qua điều kiện môi trường. Chạy lại theo `infra/test-pgvector.ps1` khi Docker/pgvector sẵn sàng.

API file hiện theo documentId, không có snapshot token gắn nguyên tử với GET source. UI kiểm tra source lại trước khi fetch PDF nhưng không hứa mạnh hơn hợp đồng backend khi publish xảy ra giữa hai request.
