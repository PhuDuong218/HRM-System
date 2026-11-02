# Giao diện Android EMPLOYEE

Giao diện Java/XML dùng màu xanh HRM (#004581), nền sáng, thẻ thông tin, nhãn trạng thái và thanh điều hướng dưới. Đối chiếu MA_TRAN_PHAN_QUYEN_DA_DIEU_CHINH.md.

## Các mục đang kết nối

- Trang chủ: hồ sơ phiên đăng nhập, công hôm nay và lối tắt cá nhân.
- Lịch: ca được phân công, lịch OT đã duyệt; xem chi tiết, check-in/check-out theo khả năng backend trả về.
- Chấm công: lịch sử công cá nhân, kết quả, người nhập/xác nhận và yêu cầu điều chỉnh/bổ sung khi hợp lệ.
- Đơn của tôi: đơn điều chỉnh công, đăng ký/sửa/hủy OT ở trạng thái cho phép, yêu cầu hủy OT đã duyệt và bổ sung công OT.
- Hồ sơ: thông tin cá nhân chỉ đọc, phiếu lương và khoản lương/phụ cấp đã công bố qua API cá nhân khi được backend cấp quyền; đổi mật khẩu và đăng xuất.

Không có danh sách nhân viên, nhập/sửa công trực tiếp, duyệt đơn, xác nhận công, tạo lịch, sửa hồ sơ liên hệ hoặc API quản trị trên app.

## API

Auth: /api/auth/login, /me, /change-password, /logout.

Công/lịch thường: /api/attendance-workspace, luôn mine=true khi truy vấn dữ liệu cá nhân. Lấy giờ chấm công tại server.

OT: /api/overtime/workspace và /calendar, /supplements. Nút thao tác lấy theo capabilities backend; không tự xác nhận hoặc suy đoán giờ nghỉ/giờ thực tế.

Lương: /api/me/payslips và /api/me/salary, xác định nhân viên từ principal và chỉ trả dữ liệu được công bố. Không dùng API /payslips/my-payslips nhận employeeId do client gửi.

## Giới hạn phiên bản giao diện này

- Hợp đồng, quyết định, bằng cấp và yêu cầu đổi lịch chưa nối API cá nhân trên app. Không dùng API quản trị để thay thế.
- Nghỉ phép/nghỉ việc chưa triển khai vì ma trận yêu cầu tham chiếu đặc tả riêng.
- Chưa có quy trình đề nghị OT vượt giờ trên mobile; vẫn theo dõi và xử lý theo các API/web hiện có.
- Bộ chọn bản ghi tạo yêu cầu dùng trang/khoảng ngày hiện tại; đổi khoảng ngày hoặc trang để tìm bản ghi khác.
- Phiên đăng nhập giữ trong bộ nhớ, không lưu mật khẩu; tiến trình bị đóng cần đăng nhập lại.

## Chạy

1. Giữ backend cổng 2005 hoạt động. Bản debug giả lập dùng http://10.0.2.2:2005.
2. Android Studio mở thư mục mobile, chọn app và Pixel 7, nhấn Run.
3. Đăng nhập bằng tài khoản EMPLOYEE hiện có. ADMIN/HR sử dụng web.
4. Phân lịch bằng ADMIN/HR trên web để thử luồng chấm công có dữ liệu thật.

Hợp lệ của công, hạn gửi, khóa kỳ, phạm vi và trạng thái vẫn do backend quyết định. Việc gửi đề nghị không thay đổi công gốc trước khi được duyệt.
