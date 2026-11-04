# Nghiệm thu giao diện duyệt và xuất bản Quiz AI

## Chuẩn bị

- Backend hỗ trợ API `/api/quizzes/{id}/review` và các thao tác review/unreview/confirm/publish/archive; frontend và API cùng origin hoặc dùng Vite proxy.
- Một Quiz AI DRAFT thuộc tài khoản kiểm thử, có ít nhất hai câu; một Quiz MANUAL để đối chiếu. Dùng dữ liệu tổng hợp trong database thử nghiệm. Không sửa database đang dùng để giả sourceType.
- Không cần gọi provider để duyệt. UI sinh Quiz là chức năng riêng; có thể dùng fixture backend hiện có khi chạy kiểm thử tích hợp.

## Luồng nghiệp vụ

- [ ] Mở Course → Bài kiểm tra → Duyệt Quiz AI. Nhãn AI và trạng thái hiển thị đúng; không mở form sửa cũ từ danh sách.
- [ ] Có nội dung hiện tại, bốn lựa chọn, đáp án đúng, Concept và số câu đã duyệt.
- [ ] Duyệt một trong hai câu: chưa xác nhận được. Duyệt đủ: xác nhận được.
- [ ] Xác nhận chuyển sang CONFIRMED nhưng chưa mở làm bài và không tự gọi publish.
- [ ] Xuất bản bằng nút riêng, có xác nhận. Reload vẫn PUBLISHED; tab Làm bài gửi bài được và cập nhật dữ liệu học tập.
- [ ] Sửa title/câu hoặc thêm/xóa câu trước publish: review/revision được tải lại; CONFIRMED quay DRAFT, câu sửa mất dấu duyệt, câu khác giữ dấu duyệt.
- [ ] Hủy duyệt tăng revision và yêu cầu xác nhận lại. Xóa hết câu không thể xác nhận.
- [ ] PUBLISHED/ARCHIVED không sửa hoặc duyệt câu; archive khóa vĩnh viễn trong phiên bản hiện tại. Archive khi đang ở tab Làm bài phải quay về nội dung để đọc.

## Đồng bộ và lỗi

- [ ] Hai tab cùng mở một Quiz: B sửa câu; A duyệt/xác nhận/xuất bản bằng bản cũ nhận 409, khóa mutation cho tới khi tải lại. Không có write tự retry bằng revision mới.
- [ ] Form đang nhập gặp 409 giữ nội dung để sao chép. Tải lại có cảnh báo mất phần chưa lưu, rồi đóng form cũ.
- [ ] CRUD thành công nhưng tải review lỗi: form lưu đóng; chỉ có tải lại, không đề nghị gửi lại thao tác đã lưu.
- [ ] Lỗi mạng/5xx không tự retry write; đối chiếu server bằng tải lại. 400 giữ form và báo lỗi; 401 kết thúc phiên; 404 không hiển thị nội dung ngoài quyền.
- [ ] Bấm nhanh hai lần chỉ gửi một write. Chuyển Course/Quiz hoặc đăng xuất khi đang tải không hiện dữ liệu cũ.

## Nguồn, tính tương thích và khả năng truy cập

- [ ] Xem nguồn dùng API có xác thực và đúng Course/Document/chunk; viewer lỗi không phá màn hình review.
- [ ] Có nhãn nội dung đã chỉnh sửa, câu không có evidence gốc và cảnh báo nguồn đã đổi/thiếu metadata. Không mô tả quote như chứng nhận đáp án.
- [ ] Quiz MANUAL vẫn hỗ trợ nhiều đáp án đúng, Đúng/Sai và trả lời ngắn; không có CONFIRMED trong dropdown, không gọi workflow AI.
- [ ] Desktop và mobile 390px không tràn ngang; thao tác bàn phím, focus khi đóng modal, nội dung dài và trạng thái loading rõ ràng.

## Kiểm thử tự động

Từ `frontend/`:

```powershell
npm test
npm run lint
npm run build
npm run test:e2e -- --workers=2
```

`e2e/quiz-review.spec.js` điều khiển response API để kiểm chứng UI, request và các nhánh lỗi một cách tái lập; không thay thế backend thật. Trace/screenshot ở `frontend/test-results/`, được ignore. Token và fixture môi trường thật chỉ lưu tạm ngoài Git.

Backend/PostgreSQL có collection hồi quy riêng tại `infra/testing/quiz-review/collection`; xem README cùng thư mục. Phân biệt rõ bằng chứng UI với API fixtures, UI với Spring/H2 thật và API với PostgreSQL. H2 không chứng minh migration/row-lock PostgreSQL. Không tính test skipped là pass.

## Kết quả kiểm chứng — 2026-11-04

| Kiểm tra | Kết quả |
| --- | --- |
| Frontend unit tests | 37 passed, 0 failed, 0 skipped |
| Playwright toàn bộ frontend với API fixtures | 66 passed, gồm 23 ca duyệt Quiz; 0 failed, 0 skipped |
| ESLint và production build | Thành công |
| Browser với Spring Boot/H2 thật, database tạm | 2 ca passed: duyệt, sửa sau xác nhận, xuất bản riêng, reload, nộp bài; hai tab gây revision conflict và tải lại |
| Postman collection lint hiện có | 16 request, 0 errors, 0 warnings |
| Review code độc lập | Không phát hiện lỗi Critical/Important; bổ sung test lifecycle, metadata thiếu và HTTP errors theo góp ý |

Đã xem ảnh desktop/mobile. Backend thử nghiệm và fixture chứa token tạm đã được dọn sau khi chạy. Chưa chạy lại PostgreSQL/pgvector hoặc collection trên PostgreSQL vì Docker daemon không hoạt động; không suy luận các bảo đảm PostgreSQL từ H2. Workflow không gọi provider thật.
