# StudyHub Frontend

Frontend của StudyHub được xây dựng bằng React 19, React Router 7 và Vite 8. Ứng dụng dùng API Spring Boot trong thư mục `../backend` để xác thực và quản lý dữ liệu học tập theo cấu trúc **Workspace → Course**.

## Yêu cầu

- Node.js **20.19+** hoặc **22.12+** và npm.
- Backend StudyHub đang chạy ở `http://localhost:8080`. Xem [hướng dẫn backend](../backend/README.md) để chuẩn bị PostgreSQL và khởi động API.

## Chạy trên máy cá nhân

Từ thư mục gốc của repository:

```powershell
cd frontend
npm ci
npm run dev
```

Mở `http://localhost:5173`. Trong chế độ phát triển, Vite chuyển các request `/api` đến `http://localhost:8080` theo cấu hình trong `vite.config.js`.

Nếu frontend và backend chạy ở hai origin khác nhau, tạo `frontend/.env.local` từ file mẫu:

```powershell
Copy-Item .env.example .env.local
```

Sau đó đặt địa chỉ backend, **không kèm dấu `/` ở cuối**:

```dotenv
VITE_API_URL=http://localhost:8080
```

Để `VITE_API_URL` trống khi dùng Vite proxy hoặc triển khai frontend và API cùng origin. Khởi động lại Vite sau khi sửa `.env.local`. Nếu gọi API trực tiếp từ một origin khác, backend cũng cần cho phép origin của frontend qua CORS; cấu hình hiện tại chưa thiết lập CORS cho cách chạy này.

## Chức năng

| Đường dẫn | Nội dung |
| --- | --- |
| `/login` | Đăng nhập bằng email và mật khẩu. |
| `/register` | Tạo tài khoản mới; đăng ký thành công sẽ chuyển về đăng nhập. |
| `/welcome` | Bảng học tập yêu cầu đăng nhập. |
| `/courses/:courseId` | Chi tiết khóa học: quản lý tài liệu và khái niệm. |

Trong bảng học tập, người dùng có thể tạo, chọn, sửa và xóa Workspace; tạo, sửa và xóa Course trong Workspace; tìm Course theo tên, lọc theo trạng thái và chuyển trang kết quả. Các trạng thái Course là `PLANNED`, `IN_PROGRESS`, `COMPLETED` và `ARCHIVED`. Khi xóa Workspace, backend cũng xóa các Course thuộc Workspace đó.

Trong chi tiết khóa học, người dùng có thể thêm, sửa, xóa và tìm tài liệu theo tiêu đề; thêm, sửa, xóa khái niệm, lọc theo mức độ hiểu và quản lý kiến thức tiên quyết trong cùng khóa học. API Document hiện chỉ lưu metadata gồm tiêu đề, tên/loại tệp và đường dẫn lưu trữ; chưa có API tải tệp lên hoặc tải tệp xuống. Quiz và AI/RAG chưa có giao diện vì chưa có API tương ứng.

Frontend lưu JWT trong `localStorage`, gửi token qua header `Authorization: Bearer <token>` cho các API được bảo vệ và kết thúc phiên khi token hết hạn.

## Cấu trúc mã nguồn

| Thư mục/tệp | Vai trò |
| --- | --- |
| `src/pages/` | Trang đăng nhập, đăng ký và bảng học tập. |
| `src/components/` | Thành phần dùng chung cho giao diện xác thực. |
| `src/auth/` | Lưu phiên, đọc JWT và bảo vệ route. |
| `src/api/` | Gọi API xác thực, Workspace và Course; tạo query tìm kiếm/phân trang. |
| `src/styles.css` | Kiểu giao diện và responsive layout. |
| `vite.config.js` | Cấu hình Vite và proxy `/api` khi phát triển. |

## Kiểm tra và build

Chạy các lệnh sau trong thư mục `frontend/`:

```powershell
npm test       # Kiểm tra query dùng cho API Course
npm run lint   # Kiểm tra mã nguồn bằng ESLint
npm run build  # Tạo bản production trong dist/
```

Dùng `npm run preview` để xem thử bản build trên máy cá nhân. Khi triển khai bản production, cấu hình web server chuyển các route frontend về `index.html` và định tuyến `/api` tới backend, hoặc đặt `VITE_API_URL` lúc build nếu API nằm ở origin khác.
