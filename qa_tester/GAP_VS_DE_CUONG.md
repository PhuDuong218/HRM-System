# Đối chiếu phạm vi còn thiếu

Đã đọc hai DOCX trong Downloads; bản trích văn bản ở evidence/. Không tự coi đặc tả tester là toàn bộ đề cương gốc.

- /allowances, /decisions, /payroll, /payslips: NOT-IMPLEMENTED (UI), hai độ rộng 1440/390 đều hiện trang chưa triển khai. Không tính là lỗi chạy, không coi đã hoàn thành mục 11–14 bằng UI.
- /accounts và /reports đã có giao diện thật; không còn placeholder theo bản kiểm này.
- /attendance hiện thông báo chưa tích hợp/xác minh API danh sách công quản lý. Chưa nghiệm thu quy trình UI tra cứu công (mục8.1).
- BUG-003: bộ lọc /qualifications và /leave-types chưa có trên UI theo mẫu thống nhất.
- Hồ sơ nhân viên hiển thị trình độ hiện tại, email/SĐT search, sắp xếp mọi danh sách: chưa có kết luận nghiệm thu đủ; không giả định thiếu chỉ từ một màn hình.
- Tệp/chứng chỉ ngày cấp–hết hạn, ảnh đại diện, thông báo nghiệp vụ, liên hệ khẩn cấp, nhập Excel, quên mật khẩu email/OTP: CHƯA KIỂM hoặc THIẾU-CHUẨN nếu sau này xác minh thiếu; không dùng làm lỗi trái đề cương hiện tại.

Tài liệu CSDL có 33 tên bảng, hrm_test hiện có 50 bảng. Bảng thêm: attendance_breaks, attendance_correction_request_details, employee_request_withdrawals, g2_leave_allocations, g2_leave_policies, g2_resignation_executions, hr_department_scopes, overtime_breaks, overtime_overflow_intervals, overtime_request_details, payroll_batch_policies, payroll_calculation_policies, payroll_insurance_bases, schedule_change_request_breaks, schedule_change_request_details, work_schedule_breaks, work_shift_breaks. Bảng tài liệu chưa tìm thấy: không. Đây là chênh phiên bản tài liệu, chưa kết luận lỗi DB. So cột/ràng buộc từng bảng: NOT-RUN.
