# Kết quả kiểm tra bộ khung

- `npm install --no-audit --no-fund`: thành công; đã tạo package-lock.json.
- `npm run build`: thành công; Angular kiểm tra TypeScript và template ở chế độ strict.
- `npm start -- --host 127.0.0.1`: máy chủ phát triển khởi động thành công.
- Môi trường kiểm tra: Node.js 24.19.0; Angular 20.3.34; TypeScript 5.9.x; Bootstrap 5.3.8. Phiên bản chính xác của phụ thuộc nằm trong package-lock.json.
- Chưa chạy được kiểm tra tương tác bằng trình duyệt: môi trường không có Chromium; tải trình duyệt thất bại. Chưa xác nhận trực quan desktop/mobile, menu hoặc các trạng thái HTTP trong trình duyệt.
- Chưa kiểm tra với backend của người dùng vì chưa được cung cấp mã nguồn hoặc địa chỉ backend truy cập được. Cần đổi target trong proxy.conf.json nếu cổng khác 8080.

## Tự kiểm tra sau khi chạy

1. Mở /overview: có sidebar, header và trang tổng quan.
2. Chọn Nhân viên: có bảng rỗng, ghi rõ chưa tích hợp API.
3. Chọn Phòng ban rồi Chức vụ: tiêu đề đổi theo menu.
4. Nhập đường dẫn không tồn tại: trang 404 xuất hiện.
5. Thu hẹp trình duyệt: bấm Menu để hiện/ẩn điều hướng.
6. Tắt backend, chọn Kiểm tra kết nối và bấm nút: nhận thông báo lỗi.
7. Chạy backend có GET /api/health, thử lại: nhận phản hồi thật. Nếu 401/403 thì đối chiếu Security, không mở toàn bộ API.

Chỉ build và khởi động dev server đã được xác nhận; các mục tự kiểm tra trên chưa được đánh dấu thành công.
