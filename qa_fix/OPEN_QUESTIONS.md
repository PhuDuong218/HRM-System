# Điểm cần ghi nhận

- SQL chỉ SELECT và yêu cầu backup DB trước ghi dữ liệu là hai ràng buộc cần giải quyết. SELECT msdb ngày 06/11/2026 chưa tìm được full backup của hrm_center_full. Chưa chạy BACKUP/RESTORE hoặc ghi dữ liệu API QAFIX khi chưa có backup. Tiếp tục source + unit/mocked tests; live write verification CHƯA VERIFY.
- Chưa thay tiến trình backend2005 hoặc cấu hình triển khai. Cần khởi động bản mới bằng các cờ tắt job ở cấp tiến trình trước live test; không tự khởi chạy job, không dùng khóa JWT mới để thay phiên người dùng.
- Quy tắc lý do hiện có trong hồ sơ: bắt buộc, tối đa1000 ký tự, chưa có ngưỡng tối thiểu. Áp dụng trim +1..1000 ký tự để giữ hành vi hợp lệ cũ, thay vì tự nâng thành5..500. Ghi rõ trong test biên.
- Không sửa test cũ; các client/test cũ gửi PUT/PATCH thiếu version sẽ nhận400 theo yêu cầu mới. Nếu regression cũ kỳ vọng200 cho request thiếu version, ghi incompatibility, không nới test.
- Source hiện tại ScheduleChangeExpiryJob không có cờ tắt; các EnableScheduling không có điều kiện. Chưa có cách dùng các cờ hiện có để đảm bảo tắt toàn bộ job theo yêu cầu. Không tự thêm cấu hình/job ngoài5lỗi cho phép.
- Không mở thêm quyền EMPLOYEE sửa hồ sơ: role/permission và quy tắc không tự sửa liên hệ được giữ nguyên. Version áp cho mọi người đã có quyền ghi (kể cả ADMIN/HR sửa chính mình); không biến yêu cầu test chủ hồ sơ thành quyền quản trị mới.
- Thư mục qa_fix là ngoại lệ cần thiết cho chính các artifact mà prompt yêu cầu; không chỉnh sửa qa_ui/qa_tester hoặc target trực tiếp. Maven/ng build tự sinh output bình thường.
