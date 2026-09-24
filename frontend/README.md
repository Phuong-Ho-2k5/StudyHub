# StudyHub Frontend

Ứng dụng React cho luồng đăng ký, đăng nhập và trang chờ sau đăng nhập của StudyHub.

## Chạy local

Yêu cầu Node.js 20.19+ hoặc 22.12+.

```powershell
cd frontend
npm install
npm run dev
```

Vite chạy frontend tại `http://localhost:5173` và chuyển tiếp request `/api` tới backend Spring Boot tại `http://localhost:8080`.

## Kiểm tra và build

```powershell
npm run lint
npm run build
```

Kết quả production nằm trong thư mục `dist`.

Khi frontend và backend được host ở hai origin khác nhau, sao chép `.env.example` thành `.env.local` và đặt `VITE_API_URL` thành địa chỉ backend. Để trống biến này khi dùng Vite proxy hoặc triển khai cùng origin.
